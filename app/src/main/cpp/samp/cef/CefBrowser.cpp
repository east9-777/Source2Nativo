#include "CefBrowser.h"

#include "../main.h"
#include "../game/game.h"
#include "../net/netgame.h"
#include "../vendor/raknet/RakClientInterface.h"
#include "../vendor/raknet/BitStream.h"
#include "../vendor/raknet/PacketEnumerations.h"
#include "../java/jniutil.h"

#include <algorithm>
#include <string>
#include <unordered_map>

extern CNetGame *pNetGame;
extern CJavaWrapper *pJavaWrapper;

namespace {
    struct BrowserState {
        bool desiredVisible = false;
        bool effectiveVisible = false;
    };

    std::unordered_map<int, BrowserState> &BrowserRegistry() {
        static std::unordered_map<int, BrowserState> registry;
        return registry;
    }

    bool &PauseHiddenFlag() {
        static bool flag = false;
        return flag;
    }

    jmethodID ResolveReceiveMethod(JNIEnv *env) {
        static jmethodID method = nullptr;
        if (method) {
            return method;
        }

        if (!CCefBrowser::clazz) {
            FLog("[CEF] Java class reference not set");
            return nullptr;
        }

        method = env->GetStaticMethodID(CCefBrowser::clazz, "receiveCefPacket", "(IILjava/lang/String;)V");
        if (!method) {
            FLog("[CEF] Unable to resolve receiveCefPacket method");
        }
        return method;
    }

    void CallJava(int browserId, int actionId, const std::string &data) {
        if (!CCefBrowser::clazz) {
            FLog("[CEF] Java class reference not set");
            return;
        }

        JNIEnv *env = CJavaWrapper::GetEnv();
        if (!env) {
            FLog("[CEF] Failed to obtain JNIEnv");
            return;
        }

        jmethodID method = ResolveReceiveMethod(env);
        if (!method) {
            return;
        }

        jstring jData = env->NewStringUTF(data.c_str());
        if (!jData) {
            FLog("[CEF] Failed to allocate jstring for payload (len=%d)", static_cast<int>(data.size()));
            return;
        }

        env->CallStaticVoidMethod(CCefBrowser::clazz, method, browserId, actionId, jData);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        env->DeleteLocalRef(jData);
    }

    std::string ReadString8(RakNet::BitStream &bs) {
        std::uint8_t len = 0;
        if (!bs.Read(len) || len == 0) {
            return {};
        }
        std::string out(len, '\0');
        bs.ReadBits(reinterpret_cast<unsigned char *>(out.data()), len * 8, true);
        return out;
    }

    void ApplyEffectiveVisibility(int browserId, bool visible) {
        auto &state = BrowserRegistry()[browserId];
        if (state.effectiveVisible == visible) {
            return;
        }

        CallJava(browserId, static_cast<int>(CCefBrowser::eCefAction::CEF_SET_VISIBLE), visible ? "true" : "false");
        state.effectiveVisible = visible;
    }
}

void CCefBrowser::ReceivePacket(Packet *p) {
    if (!p) {
        return;
    }

    RakNet::BitStream bs(reinterpret_cast<unsigned char *>(p->data), p->length, false);
    bs.IgnoreBits(8); // skip packet id

    uint32_t browserId = 0;
    uint32_t actionId = 0;

    if (!bs.Read(browserId) || !bs.Read(actionId)) {
        FLog("[CEF] Failed to read header from packet");
        return;
    }

    std::string payload = ReadString8(bs);
    CallJava(static_cast<int>(browserId), static_cast<int>(actionId), payload);
}

void CCefBrowser::CreateBrowser(int browserId, int x, int y, int width, int height, const std::string &url,
                                bool visible, int bgAlpha, bool offscreen) {
    const bool effectiveVisible = PauseHiddenFlag() ? false : visible;

    std::string data = std::to_string(x) + "," + std::to_string(y) + "," +
                       std::to_string(width) + "," + std::to_string(height) + "," +
                       url + "," + (effectiveVisible ? "true" : "false") + "," +
                       std::to_string(bgAlpha) + "," + (offscreen ? "true" : "false");
    CallJava(browserId, static_cast<int>(eCefAction::CEF_CREATE), data);

    auto &state = BrowserRegistry()[browserId];
    state.desiredVisible = visible;
    state.effectiveVisible = effectiveVisible;
}

void CCefBrowser::DestroyBrowser(int browserId) {
    CallJava(browserId, static_cast<int>(eCefAction::CEF_DESTROY), "");
    BrowserRegistry().erase(browserId);
}

void CCefBrowser::LoadUrl(int browserId, const std::string &url) {
    CallJava(browserId, static_cast<int>(eCefAction::CEF_LOAD_URL), url);
}

void CCefBrowser::Reload(int browserId) {
    CallJava(browserId, static_cast<int>(eCefAction::CEF_RELOAD), "");
}

void CCefBrowser::Stop(int browserId) {
    CallJava(browserId, static_cast<int>(eCefAction::CEF_STOP), "");
}

void CCefBrowser::EvaluateJavaScript(int browserId, const std::string &script) {
    CallJava(browserId, static_cast<int>(eCefAction::CEF_EVAL_JS), script);
}

void CCefBrowser::SetBounds(int browserId, int x, int y, int width, int height) {
    std::string data = std::to_string(x) + "," + std::to_string(y) + "," +
                       std::to_string(width) + "," + std::to_string(height);
    CallJava(browserId, static_cast<int>(eCefAction::CEF_SET_BOUNDS), data);
}

void CCefBrowser::SetVisible(int browserId, bool visible) {
    auto &state = BrowserRegistry()[browserId];
    state.desiredVisible = visible;

    if (PauseHiddenFlag()) {
        return;
    }

    ApplyEffectiveVisibility(browserId, visible);
}

void CCefBrowser::SetFocus(int browserId, bool focused) {
    CallJava(browserId, static_cast<int>(eCefAction::CEF_SET_FOCUS), focused ? "true" : "false");
}

void CCefBrowser::SetBackgroundAlpha(int browserId, int alpha) {
    CallJava(browserId, static_cast<int>(eCefAction::CEF_SET_BG_ALPHA), std::to_string(alpha));
}

void CCefBrowser::SetInteractive(int browserId, bool interactive) {
    CallJava(browserId, static_cast<int>(eCefAction::CEF_SET_INTERACTIVE), interactive ? "true" : "false");
}

void CCefBrowser::HandlePauseState(bool paused) {
    auto &pausedFlag = PauseHiddenFlag();
    auto &registry = BrowserRegistry();

    if (paused) {
        if (pausedFlag) {
            return;
        }

        pausedFlag = true;
        for (const auto &entry : registry) {
            ApplyEffectiveVisibility(entry.first, false);
        }
    } else {
        if (!pausedFlag) {
            return;
        }

        pausedFlag = false;
        for (const auto &entry : registry) {
            ApplyEffectiveVisibility(entry.first, entry.second.desiredVisible);
        }
    }
}

void CCefBrowser::HandleMouseMove(int browserId, int x, int y) {
    CallJava(browserId, static_cast<int>(eCefAction::CEF_INPUT_MOUSE_MOVE),
             std::to_string(x) + "," + std::to_string(y));
}

void CCefBrowser::HandleMouseDown(int browserId, int x, int y, int button) {
    CallJava(browserId, static_cast<int>(eCefAction::CEF_INPUT_MOUSE_DOWN),
             std::to_string(x) + "," + std::to_string(y) + "," + std::to_string(button));
}

void CCefBrowser::HandleMouseUp(int browserId, int x, int y, int button) {
    CallJava(browserId, static_cast<int>(eCefAction::CEF_INPUT_MOUSE_UP),
             std::to_string(x) + "," + std::to_string(y) + "," + std::to_string(button));
}

void CCefBrowser::HandleScroll(int browserId, int deltaX, int deltaY) {
    CallJava(browserId, static_cast<int>(eCefAction::CEF_INPUT_SCROLL),
             std::to_string(deltaX) + "," + std::to_string(deltaY));
}

void CCefBrowser::HandleKeyDown(int browserId, int keyCode) {
    CallJava(browserId, static_cast<int>(eCefAction::CEF_INPUT_KEY_DOWN), std::to_string(keyCode));
}

void CCefBrowser::HandleKeyUp(int browserId, int keyCode) {
    CallJava(browserId, static_cast<int>(eCefAction::CEF_INPUT_KEY_UP), std::to_string(keyCode));
}

void CCefBrowser::HandleTextInput(int browserId, const std::string &text) {
    CallJava(browserId, static_cast<int>(eCefAction::CEF_INPUT_TEXT), text);
}

void CCefBrowser::SendEvent(eCefEvent eventType, int browserId, const std::string &data) {
    if (!pNetGame || !pNetGame->GetRakClient()) {
        return;
    }

    RakNet::BitStream bs;
    bs.Write(CCefBrowser::PACKET_ID);
    bs.Write(static_cast<uint32_t>(browserId));
    bs.Write(static_cast<uint32_t>(eventType));
    uint8_t len = static_cast<uint8_t>(std::min<size_t>(data.size(), 255));
    bs.Write(len);
    if (len > 0) {
        bs.WriteBits(reinterpret_cast<const unsigned char *>(data.data()), len * 8, true);
    }

    pNetGame->GetRakClient()->Send(&bs, HIGH_PRIORITY, RELIABLE_ORDERED, 4);
}

void CCefBrowser::SendEvent(eCefEvent eventType, int browserId, const std::string &data1, const std::string &data2) {
    if (!pNetGame || !pNetGame->GetRakClient()) {
        return;
    }

    RakNet::BitStream bs;
    bs.Write(CCefBrowser::PACKET_ID);
    bs.Write(static_cast<uint32_t>(browserId));
    bs.Write(static_cast<uint32_t>(eventType));

    uint8_t len1 = static_cast<uint8_t>(std::min<size_t>(data1.size(), 255));
    bs.Write(len1);
    if (len1 > 0) {
        bs.WriteBits(reinterpret_cast<const unsigned char *>(data1.data()), len1 * 8, true);
    }

    uint8_t len2 = static_cast<uint8_t>(std::min<size_t>(data2.size(), 255));
    bs.Write(len2);
    if (len2 > 0) {
        bs.WriteBits(reinterpret_cast<const unsigned char *>(data2.data()), len2 * 8, true);
    }

    pNetGame->GetRakClient()->Send(&bs, HIGH_PRIORITY, RELIABLE_ORDERED, 4);
}

void CCefBrowser::SendEvent(eCefEvent eventType, int browserId, int data) {
    if (!pNetGame || !pNetGame->GetRakClient()) {
        return;
    }

    RakNet::BitStream bs;
    bs.Write(CCefBrowser::PACKET_ID);
    bs.Write(static_cast<uint32_t>(browserId));
    bs.Write(static_cast<uint32_t>(eventType));
    bs.Write(data);

    pNetGame->GetRakClient()->Send(&bs, HIGH_PRIORITY, RELIABLE_ORDERED, 4);
}

extern "C" JNIEXPORT void JNICALL
Java_com_raiferoleplay_game_cef_CefBrowser_nativeSendEvent(JNIEnv *env, jobject /*thiz*/, jint browserId,
                                                      jint eventType, jstring data) {
    if (!pNetGame || !pNetGame->GetRakClient()) {
        return;
    }

    std::string payload;
    const char *raw = env->GetStringUTFChars(data, nullptr);
    if (raw) {
        payload.assign(raw);
        env->ReleaseStringUTFChars(data, raw);
    }

    RakNet::BitStream bs;
    bs.Write(CCefBrowser::PACKET_ID);
    bs.Write(static_cast<uint32_t>(browserId));
    bs.Write(static_cast<uint32_t>(eventType));

    uint8_t len = static_cast<uint8_t>(std::min<size_t>(payload.size(), 255));
    bs.Write(len);
    if (len > 0) {
        bs.WriteBits(reinterpret_cast<const unsigned char *>(payload.data()), len * 8, true);
    }

    pNetGame->GetRakClient()->Send(&bs, HIGH_PRIORITY, RELIABLE_ORDERED, 4);
}
