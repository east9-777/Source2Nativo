#pragma once

#include <cstdint>
#include <string>
#include <jni.h>

struct Packet;

class CCefBrowser {
public:
    static inline jclass clazz = nullptr;

    enum class eCefAction {
        CEF_CREATE = 1,
        CEF_DESTROY,
        CEF_LOAD_URL,
        CEF_RELOAD,
        CEF_STOP,
        CEF_EVAL_JS,
        CEF_SET_BOUNDS,
        CEF_SET_VISIBLE,
        CEF_SET_FOCUS,
        CEF_SET_BG_ALPHA,
        CEF_INPUT_MOUSE_MOVE,
        CEF_INPUT_MOUSE_DOWN,
        CEF_INPUT_MOUSE_UP,
        CEF_INPUT_SCROLL,
        CEF_INPUT_KEY_DOWN,
        CEF_INPUT_KEY_UP,
        CEF_INPUT_TEXT,
        CEF_SET_INTERACTIVE
    };

    enum class eCefEvent {
        CEF_EVENT_LOADED = 101,
        CEF_EVENT_URL_CHANGED,
        CEF_EVENT_TITLE_CHANGED,
        CEF_EVENT_CONSOLE,
        CEF_EVENT_ERROR,
        CEF_EVENT_CLICK
    };

    static constexpr std::uint8_t PACKET_ID = 255; // Must match PACKET_CEF_RPC on the server

    static void ReceivePacket(Packet *p);

    static void CreateBrowser(int browserId, int x, int y, int width, int height, const std::string &url,
                              bool visible = true, int bgAlpha = 255, bool offscreen = false);
    static void DestroyBrowser(int browserId);
    static void LoadUrl(int browserId, const std::string &url);
    static void Reload(int browserId);
    static void Stop(int browserId);
    static void EvaluateJavaScript(int browserId, const std::string &script);
    static void SetBounds(int browserId, int x, int y, int width, int height);
    static void SetVisible(int browserId, bool visible);
    static void SetFocus(int browserId, bool focused);
    static void SetBackgroundAlpha(int browserId, int alpha);
    static void SetInteractive(int browserId, bool interactive);
    static void HandlePauseState(bool paused);

    static void HandleMouseMove(int browserId, int x, int y);
    static void HandleMouseDown(int browserId, int x, int y, int button);
    static void HandleMouseUp(int browserId, int x, int y, int button);
    static void HandleScroll(int browserId, int deltaX, int deltaY);
    static void HandleKeyDown(int browserId, int keyCode);
    static void HandleKeyUp(int browserId, int keyCode);
    static void HandleTextInput(int browserId, const std::string &text);

    static void SendEvent(eCefEvent eventType, int browserId, const std::string &data);
    static void SendEvent(eCefEvent eventType, int browserId, const std::string &data1, const std::string &data2);
    static void SendEvent(eCefEvent eventType, int browserId, int data);
};
