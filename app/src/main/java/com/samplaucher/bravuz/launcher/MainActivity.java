package com.raiferoleplay.game.launcher; // Mude para este que é o caminho real visto na print

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;
import android.content.Intent;
import java.util.List;
import java.util.ArrayList;

import androidx.appcompat.app.AppCompatActivity;

import com.raiferoleplay.game.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import com.raiferoleplay.game.launcher.util.SharedPreferenceCore;
import com.raiferoleplay.game.launcher.util.SignatureChecker;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.CompoundButton;
import android.util.Log;
import android.widget.Switch;

public class MainActivity extends AppCompatActivity {

    private TextView serverNameText;
    private LinearLayout serverCard, playButton;
    private View btnSettings;
    private boolean carregouServidor = false;
    private JSONArray servidoresCache = null;

    /*
     * The original launcher tried to load this list from a private LAN address
     * (192.168.0.114). That address is not reachable from a player's phone and
     * left the launcher permanently in the loading state.
     *
     * Keep the game endpoint here as the single source of truth. The list is
     * local on purpose: the SA-MP game server is not an HTTP API and cannot be
     * used to return the JSON that the old launcher expected.
     */
    private static final String SERVER_NAME = "Nativo RPG | BETA";
    private static final String SERVER_HOST = "172.96.140.62";
    private static final int SERVER_PORT = 3255;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );

        serverCard = findViewById(R.id.serverCard);
        serverNameText = findViewById(R.id.serverName1);
        playButton = findViewById(R.id.playButton);
        btnSettings = findViewById(R.id.topRightIcon);
        btnSettings.setOnClickListener(v -> {
            if(!carregouServidor) {
                Toast.makeText(this, "Carregando Aguarde...", Toast.LENGTH_SHORT).show();
                return;
            }
            abrirDialogSettings();
        });
        serverCard.setOnClickListener(v -> {
            if (!carregouServidor) {
                Toast.makeText(this, "Carregando servidores...", Toast.LENGTH_SHORT).show();
                return;
            }
            abrirDialogServidores();
        });

        playButton.setOnClickListener(v -> {
            if (!carregouServidor) {
                Toast.makeText(this, "Aguarde...", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                iniciarSAMP();
            } catch (Exception e) {
                Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        carregarServidores();
    }

    // =====================================================
    // Carrega o servidor configurado uma única vez.
    private void carregarServidores() {
        new Thread(() -> {
            servidoresCache = criarListaServidoresPadrao();
            JSONObject salvo = carregarServidorSalvo();

            runOnUiThread(() -> {
                try {
                    if (servidoresCache == null || servidoresCache.length() == 0) {
                        serverNameText.setText("Erro API");
                        return;
                    }

                    JSONObject escolhido = (salvo != null)
                            ? verificarServidorNaAPI(salvo, servidoresCache)
                            : null;

                    if (escolhido == null)
                        escolhido = servidoresCache.getJSONObject(0);

                    serverNameText.setText(escolhido.getString("name"));

                    salvarServidorSelecionado(
                            escolhido.getString("name"),
                            escolhido.getString("ip"),
                            escolhido.getString("port")
                    );

                    carregouServidor = true;

                } catch (Exception e) {
                    Log.e("MainActivity", "Erro ao carregar servidor", e);
                    serverNameText.setText("Erro ao carregar");
                    carregouServidor = false;
                }
            });
        }).start();
    }

    private JSONArray criarListaServidoresPadrao() {
        JSONArray servidores = new JSONArray();
        JSONObject servidor = new JSONObject();
        try {
            servidor.put("name", SERVER_NAME);
            servidor.put("ip", SERVER_HOST);
            servidor.put("port", String.valueOf(SERVER_PORT));
            servidor.put("players", "Servidor online");
            servidores.put(servidor);
        } catch (Exception e) {
            Log.e("MainActivity", "Erro ao criar servidor padrão", e);
        }
        return servidores;
    }

    // =====================================================
    private void abrirDialogServidores() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_servers);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        LinearLayout serverList = dialog.findViewById(R.id.serverList);

        try {
            for (int i = 0; i < servidoresCache.length(); i++) {
                JSONObject serv = servidoresCache.getJSONObject(i);
                View item = getLayoutInflater().inflate(R.layout.item_server, null);

                ((TextView) item.findViewById(R.id.serverName))
                        .setText(serv.getString("name"));

                ((TextView) item.findViewById(R.id.serverPlayers))
                        .setText(serv.getString("players"));

                item.setOnClickListener(v -> {
                    dialog.dismiss();
                    try {
                        serverNameText.setText(serv.getString("name"));
                        salvarServidorSelecionado(
                                serv.getString("name"),
                                serv.getString("ip"),
                                serv.getString("port")
                        );
                    } catch (Exception ignored) {}
                });

                serverList.addView(item);
            }
        } catch (Exception ignored) {}

        dialog.show();
    }

    // =====================================================
    private JSONObject verificarServidorNaAPI(JSONObject salvo, JSONArray api) {
        try {
            for (int i = 0; i < api.length(); i++) {
                JSONObject s = api.getJSONObject(i);
                if (s.getString("ip").equals(salvo.getString("ip")) &&
                        s.getString("port").equals(salvo.getString("port"))) {
                    return s;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private JSONObject carregarServidorSalvo() {
        try {
            File file = new File(getExternalFilesDir(null) + "/SAMP/server.json");
            if (!file.exists()) return null;

            BufferedReader br = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
            br.close();

            return new JSONObject(sb.toString());
        } catch (Exception e) {
            return null;
        }
    }

    // =====================================================
    private void salvarServidorSelecionado(String nome, String ip, String port) {
        try {
            File dir = new File(getExternalFilesDir(null) + "/SAMP/");
            if (!dir.exists()) dir.mkdirs();

            JSONObject obj = new JSONObject();
            obj.put("name", nome);
            obj.put("ip", ip);
            obj.put("port", port);
            atualizarSettingsIni(ip, port);
            FileWriter fw = new FileWriter(new File(dir, "server.json"));
            fw.write(obj.toString());
            fw.close();
        } catch (Exception ignored) {}
    }

    // =====================================================
    private void atualizarSettingsIni(String ip, String port) {
        try {
            File dir = new File(getExternalFilesDir(null) + "/SAMP/");
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, "settings.ini");

            if (!file.exists()) {
                FileWriter fw = new FileWriter(file);
                fw.write("[client]\nname=launcher\nhost=" + ip + "\nport=" + port + "\n\n");
                fw.write("[debug]\ndebug=false\nonline=true\n\n");
                fw.write("[gui]\nFont=arial.ttf\nFontSize=30\nFontOutline=2\nChatMaxMessages=6\nandroidkeyboard=0\nfps_limit=60\nShowFPS=true\nChatShadow=true\nChatBackground=true\n");
                fw.close();
                return;
            }

            BufferedReader br = new BufferedReader(new FileReader(file));
            StringBuilder out = new StringBuilder();
            String line;
            boolean client = false;
            boolean hostAtualizado = false;
            boolean portaAtualizada = false;

            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.equalsIgnoreCase("[client]")) {
                    client = true;
                } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    if (client) {
                        if (!hostAtualizado) {
                            out.append("host=").append(ip).append("\n");
                            hostAtualizado = true;
                        }
                        if (!portaAtualizada) {
                            out.append("port=").append(port).append("\n");
                            portaAtualizada = true;
                        }
                    }
                    client = false;
                } else if (client) {
                    int separator = trimmed.indexOf('=');
                    if (separator > 0) {
                        String key = trimmed.substring(0, separator).trim();
                        if (key.equalsIgnoreCase("host")) {
                            line = "host=" + ip;
                            hostAtualizado = true;
                        } else if (key.equalsIgnoreCase("port")) {
                            line = "port=" + port;
                            portaAtualizada = true;
                        }
                    }
                }
                out.append(line).append("\n");
            }
            br.close();

            if (client) {
                if (!hostAtualizado) out.append("host=").append(ip).append("\n");
                if (!portaAtualizada) out.append("port=").append(port).append("\n");
            }

            FileWriter fw = new FileWriter(file);
            fw.write(out.toString());
            fw.close();

        } catch (Exception ignored) {}
    }

    // Simple INI reader: looks for first occurrence of key= and returns value or default
    private String lerIni(String key, String def) {
        try {
            File file = new File(getExternalFilesDir(null) + "/SAMP/settings.ini");
            if (!file.exists()) return def;

            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") || line.length() == 0) continue;
                int idx = line.indexOf('=');
                if (idx <= 0) continue;
                String k = line.substring(0, idx).trim();
                if (k.equalsIgnoreCase(key)) {
                    String v = line.substring(idx + 1).trim();
                    br.close();
                    return v;
                }
            }
            br.close();
        } catch (Exception ignored) {}
        return def;
    }

    // Simple INI writer: replace first key= occurrence or insert under [client] section or append
    private void salvarIni(String key, String value) {
        try {
            File dir = new File(getExternalFilesDir(null) + "/SAMP/");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "settings.ini");
            if (!file.exists()) {
                // create basic structure
                FileWriter fw = new FileWriter(file);
                fw.write("[client]\n");
                fw.write(key + "=" + value + "\n");
                fw.close();
                return;
            }

            // read lines
            BufferedReader br = new BufferedReader(new FileReader(file));
            StringBuilder out = new StringBuilder();
            String line;
            boolean replaced = false;
            boolean inClient = false;
            List<String> lines = new java.util.ArrayList<>();
            while ((line = br.readLine()) != null) lines.add(line);
            br.close();

            for (int i = 0; i < lines.size(); i++) {
                String l = lines.get(i);
                String t = l.trim();
                if (t.equalsIgnoreCase("[client]")) {
                    inClient = true;
                    out.append(l).append("\n");
                    continue;
                }
                if (t.startsWith("[") && t.endsWith("]")) {
                    // leaving client section
                    if (inClient && !replaced) {
                        out.append(key).append("=").append(value).append("\n");
                        replaced = true;
                    }
                    inClient = false;
                    out.append(l).append("\n");
                    continue;
                }
                int idx = t.indexOf('=');
                if (idx > 0) {
                    String k = t.substring(0, idx).trim();
                    if (!replaced && k.equalsIgnoreCase(key)) {
                        out.append(key).append("=").append(value).append("\n");
                        replaced = true;
                        continue;
                    }
                }
                out.append(l).append("\n");
            }

            if (!replaced) {
                // try to append under [client] or at end
                String content = out.toString();
                int clientIdx = content.toLowerCase().indexOf("[client]");
                if (clientIdx >= 0) {
                    // insert after [client]
                    int insertPos = content.indexOf("\n", clientIdx);
                    if (insertPos >= 0) {
                        String before = content.substring(0, insertPos + 1);
                        String after = content.substring(insertPos + 1);
                        content = before + key + "=" + value + "\n" + after;
                    } else {
                        content = content + "\n" + key + "=" + value + "\n";
                    }
                } else {
                    content = content + "\n[client]\n" + key + "=" + value + "\n";
                }
                out = new StringBuilder(content);
            }

            FileWriter fw = new FileWriter(file);
            fw.write(out.toString());
            fw.close();

        } catch (Exception ignored) {}
    }
    private void abrirDialogSettings() {
        try {
            Dialog dialog = new Dialog(this);
            dialog.setContentView(R.layout.dialog_settings);
            if (dialog.getWindow() != null)
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            EditText nick = dialog.findViewById(R.id.dialog_nick);
            Switch keyboard = dialog.findViewById(R.id.dialog_keyboard);
            Spinner chatLines = dialog.findViewById(R.id.dialog_chat_lines);
            Spinner fpsSpinner = dialog.findViewById(R.id.dialog_fps);
            TextView close = dialog.findViewById(R.id.dialog_close);

            File dir = new File(getExternalFilesDir(null) + "/SAMP/");
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, "settings.ini");

            if (!file.exists()) {
                FileWriter fw = new FileWriter(file);
                fw.write("[client]\nname=Nick_Name\nhost=" + SERVER_HOST + "\nport=" + SERVER_PORT + "\n\n");
                fw.write("[debug]\ndebug=false\nonline=true\n\n");
                fw.write("[gui]\nFont=arial.ttf\nFontSize=30\nFontOutline=2\nChatMaxMessages=10\nandroidkeyboard=0\nfps_limit=60\nShowFPS=true\nChatShadow=true\nChatBackground=true\n");
                fw.close();
            }
            // ===== Carregar valores =====
            if (nick != null) nick.setText(lerIni("name", "launcher"));

            if (keyboard != null) keyboard.setChecked(
                    "1".equalsIgnoreCase(lerIni("androidkeyboard", "0"))
            );

            String[] chatOptions = {"5", "10", "15", "20"};
            if (chatLines != null) chatLines.setAdapter(new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    chatOptions
            ));

            int chatAtual = Integer.parseInt(lerIni("ChatMaxMessages", "10"));
            if (chatLines != null) chatLines.setSelection(chatAtual == 5 ? 0 : chatAtual == 10 ? 1 : chatAtual == 15 ? 2 : 3);

            String[] fpsOptions = {"30", "60", "90"};
            if (fpsSpinner != null) fpsSpinner.setAdapter(new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    fpsOptions
            ));

            int fpsAtual = Integer.parseInt(lerIni("fps_limit", "60"));
            if (fpsSpinner != null) fpsSpinner.setSelection(fpsAtual == 30 ? 0 : fpsAtual == 60 ? 1 : 2);

            // ===== Salvar mudanças =====
            if (nick != null) nick.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
                @Override public void afterTextChanged(Editable s){}
                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    salvarIni("name", s.toString());
                }
            });

            if (keyboard != null) keyboard.setOnCheckedChangeListener((b, v) ->
                    salvarIni("androidkeyboard", String.valueOf(v))
            );

            if (chatLines != null) chatLines.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> a, View v, int i, long l) {
                    salvarIni("ChatMaxMessages", chatOptions[i]);
                }
                @Override public void onNothingSelected(AdapterView<?> a){}
            });

            if (fpsSpinner != null) fpsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> a, View v, int i, long l) {
                    salvarIni("fps_limit", fpsOptions[i]);
                }
                @Override public void onNothingSelected(AdapterView<?> a){}
            });

            if (close != null) close.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
        } catch (Exception e) {
            Log.e("MainActivity", "abrirDialogSettings error", e);
            Toast.makeText(this, "Erro ao abrir configurações: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    // =====================================================
    private void iniciarSAMP() {
        // O host e a porta são gravados pelo servidor selecionado acima.
        // Não sobrescreva essa seleção com o endereço privado antigo.
        File file = new File(getExternalFilesDir(null) + "/SAMP/settings.ini");
        if (!file.exists()) {
            atualizarSettingsIni(SERVER_HOST, String.valueOf(SERVER_PORT));
        }

        // MONETLOADER
        if (new SharedPreferenceCore().getBoolean(this, "MLOADER")) {
            File profile = new File(
                    getExternalMediaDirs()[0] + "/monetloader/compat/profile.json"
            );

            if (!profile.exists()) {
                profile.getParentFile().mkdirs();
                try (FileWriter writer = new FileWriter(profile)) {
                    writer.write("{\n" +
                            "  \"gtasa_name\": \"libGTASA.so\",\n" +
                            "  \"profile_name\": \"SA-MP 0.3.7\",\n" +
                            "  \"compat_scripts\": [],\n" +
                            "  \"samp_name\": \"libsamp.so\",\n" +
                            "  \"receiveignorerpc_pattern\": \"F0B503AF2DE900????B004460068C16A20468847\",\n" +
                            "  \"cnetgame_ctor_pattern\": \"F0B503AF2DE9000788B00D46????9146????0446002079447A44\",\n" +
                            "  \"rakclientinterface_netgame_offset\": 528,\n" +
                            "  \"use_samp_touch_workaround\": true,\n" +
                            "  \"nveventinsertnewest_offset\": 2606320\n" +
                            "}");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        /*// VERIFICA ARQUIVOS
        File text = new File(getExternalFilesDir(null) + "SAMP/Text/american.gxt");
        File font = new File(getExternalFilesDir(null) + "SAMP/Textures/fonts/RussianFont.png");

        if (!text.exists() && !font.exists()) {
            Toast.makeText(
                    this,
                    "off game",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }*/

        // INICIA O JOGO
        startActivity(new Intent(this, com.raiferoleplay.game.game.SAMP.class));
        finish();
    }
}