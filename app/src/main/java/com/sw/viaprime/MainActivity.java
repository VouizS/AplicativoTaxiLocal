package com.sw.viaprime;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.location.*;
import android.net.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity implements LocationListener {
    private static final int GOLD = Color.rgb(215, 167, 59);
    private static final int GOLD2 = Color.rgb(238, 202, 104);
    private static final int BLACK = Color.rgb(5, 5, 5);
    private static final int CARD = Color.rgb(11, 11, 11);
    private static final int WHITE = Color.rgb(245, 240, 230);
    private static final double FALLBACK_LAT = -16.94155;
    private static final double FALLBACK_LNG = -50.44485;
    private static final String VERSION = "0.1.2";

    private final ITileSource TILE_CARTO_HD = new XYTileSource("Via Prime HD", 0, 20, 512, "@2x.png", new String[]{
            "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
    });
    private final ITileSource TILE_DARK_HD = new XYTileSource("Via Prime Dark", 0, 20, 512, "@2x.png", new String[]{
            "https://a.basemaps.cartocdn.com/rastertiles/dark_all/",
            "https://b.basemaps.cartocdn.com/rastertiles/dark_all/",
            "https://c.basemaps.cartocdn.com/rastertiles/dark_all/",
            "https://d.basemaps.cartocdn.com/rastertiles/dark_all/"
    });

    private FrameLayout root;
    private MapView map;
    private LinearLayout topCard, bottomCard, menuRow;
    private TextView topTitle, topSubtitle, topSmall, bottomTitle, bottomText;
    private Button primaryBtn, secondaryBtn, thirdBtn, menuBtn, lanBtn, centerBtn, mapBtn;
    private Marker meMarker, driverMarker, clientMarker;
    private LocationManager locationManager;
    private SharedPreferences prefs;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable tick;
    private String mode = "home";
    private String serverUrl = "http://127.0.0.1:8080";
    private String mapMode = "hd";
    private double myLat = FALLBACK_LAT;
    private double myLng = FALLBACK_LNG;
    private float myAccuracy = -1;
    private boolean hasLocation = false;
    private boolean centeredOnce = false;
    private boolean driverOnlineLocal = false;
    private JSONObject lastState;

    private String driverName = "Motorista demo";
    private String vehicleModel = "Sedan executivo";
    private String vehicleColor = "Preto";
    private String vehiclePlate = "VP-0001";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("vp_demo", MODE_PRIVATE);
        serverUrl = prefs.getString("serverUrl", serverUrl);
        driverName = prefs.getString("driverName", driverName);
        vehicleModel = prefs.getString("vehicleModel", vehicleModel);
        vehicleColor = prefs.getString("vehicleColor", vehicleColor);
        vehiclePlate = prefs.getString("vehiclePlate", vehiclePlate);
        mapMode = prefs.getString("mapMode", mapMode);
        Configuration.getInstance().setUserAgentValue("ViaPrimeDemo/" + VERSION + " (Android demo)");
        showHome();
    }

    private void showHome() {
        mode = "home";
        stopTick();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(dp(24), dp(24), dp(24), dp(24));
        page.setBackgroundColor(Color.BLACK);
        setContentView(page);

        TextView crown = text("♛", 54, Color.rgb(70,70,70), true);
        crown.setGravity(Gravity.CENTER);
        page.addView(crown);
        TextView title = text("VIA PRIME", 38, GOLD, true);
        title.setLetterSpacing(.16f);
        title.setGravity(Gravity.CENTER);
        page.addView(title);
        TextView sub = text("TRANSPORTE EXECUTIVO", 18, WHITE, true);
        sub.setLetterSpacing(.14f);
        sub.setGravity(Gravity.CENTER);
        page.addView(sub);
        addSpace(page, 18);
        TextView desc = text("Protótipo funcional v"+VERSION+" • Mapa HD • Localização real • Motoristas próximos", 14, Color.rgb(170,164,154), false);
        desc.setGravity(Gravity.CENTER);
        page.addView(desc);
        addSpace(page, 26);

        page.addView(homeButton("Entrar como Cliente", true, v -> showMapMode("client")));
        page.addView(homeButton("Entrar como Motorista", true, v -> showMapMode("driver")));
        page.addView(homeButton("Entrar como Central/Admin", false, v -> showMapMode("admin")));
        page.addView(homeButton("Privacidade e permissões", false, v -> showPrivacy()));
    }

    private Button homeButton(String label, boolean filled, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(filled ? Color.BLACK : WHITE);
        b.setBackground(filled ? goldBg() : strokeBg());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(72));
        lp.setMargins(0, dp(7), 0, dp(7));
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private void showPrivacy() {
        mode = "privacy";
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(30), dp(22), dp(22));
        page.setBackgroundColor(Color.BLACK);
        setContentView(page);
        Button back = homeButton("← Voltar", false, v -> showHome());
        page.addView(back, new LinearLayout.LayoutParams(-1, dp(56)));
        addSpace(page, 18);
        page.addView(text("Privacidade e permissões", 26, GOLD, true));
        addSpace(page, 14);
        page.addView(text("Esta demonstração usa apenas recursos necessários para provar o funcionamento real do produto.", 16, WHITE, false));
        addSpace(page, 18);
        page.addView(text("Usa: internet, localização durante o uso e mapa real.", 16, WHITE, true));
        addSpace(page, 8);
        page.addView(text("Não usa: SMS, contatos, câmera, microfone, arquivos, acessibilidade ou administrador do dispositivo.", 16, WHITE, false));
        addSpace(page, 18);
        page.addView(text("A localização é usada para mostrar cliente e motorista no mapa durante a demonstração.", 15, Color.rgb(190,184,174), false));
    }

    private void showMapMode(String m) {
        mode = m;
        centeredOnce = false;
        root = new FrameLayout(this);
        setContentView(root);

        Configuration.getInstance().load(this, prefs);
        map = new MapView(this);
        map.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true);
        map.setMinZoomLevel(4.0);
        map.setMaxZoomLevel(20.0);
        applyMapSource();
        map.getController().setZoom(17.5);
        map.getController().setCenter(new GeoPoint(FALLBACK_LAT, FALLBACK_LNG));
        root.addView(map);

        makeOverlayUi();
        updateTop("Buscando localização real...", "Conectando à central da demo");
        requestLocation();
        startTick();
    }

    private void makeOverlayUi() {
        topCard = new LinearLayout(this); topCard.setOrientation(LinearLayout.VERTICAL); topCard.setPadding(dp(18), dp(12), dp(18), dp(10)); topCard.setBackground(round(CARD, dp(18), GOLD, 1));
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, -2); topLp.setMargins(dp(18), dp(18), dp(18), 0); root.addView(topCard, topLp);
        topTitle = text(titleForMode(), 21, GOLD, true); topCard.addView(topTitle);
        topSubtitle = text("Buscando localização real...", 14, WHITE, false); topCard.addView(topSubtitle);
        topSmall = text("", 13, Color.rgb(160,154,145), false); topCard.addView(topSmall);

        menuRow = new LinearLayout(this); menuRow.setOrientation(LinearLayout.HORIZONTAL); menuRow.setGravity(Gravity.RIGHT); menuRow.setPadding(0,0,0,0);
        FrameLayout.LayoutParams rowLp = new FrameLayout.LayoutParams(-1, dp(54)); rowLp.setMargins(dp(18), dp(134), dp(18), 0); root.addView(menuRow, rowLp);
        menuBtn = miniButton("Menu", v -> showHome()); menuRow.addView(menuBtn, miniLp());
        lanBtn = miniButton("LAN", v -> showLanDialog()); menuRow.addView(lanBtn, miniLp());
        centerBtn = miniButton("Centro", v -> centerOnMe()); menuRow.addView(centerBtn, miniLp());
        mapBtn = miniButton("Mapa", v -> cycleMapMode()); menuRow.addView(mapBtn, miniLp());

        bottomCard = new LinearLayout(this); bottomCard.setOrientation(LinearLayout.VERTICAL); bottomCard.setPadding(dp(20), dp(16), dp(20), dp(18)); bottomCard.setBackground(round(CARD, dp(22), GOLD, 1));
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM); bottomLp.setMargins(dp(18), 0, dp(18), dp(20)); root.addView(bottomCard, bottomLp);
        bottomTitle = text("", 22, GOLD, true); bottomCard.addView(bottomTitle);
        bottomText = text("", 15, WHITE, false); bottomCard.addView(bottomText);
        addSpace(bottomCard, 12);
        primaryBtn = bigButton("", true); bottomCard.addView(primaryBtn);
        secondaryBtn = bigButton("", false); bottomCard.addView(secondaryBtn);
        thirdBtn = bigButton("", false); bottomCard.addView(thirdBtn);
        renderBottom(null);
    }

    private String titleForMode() {
        if ("driver".equals(mode)) return "Via Prime Motoristas";
        if ("admin".equals(mode)) return "Via Prime Central";
        return "Via Prime Cliente";
    }

    private void renderBottom(JSONObject st) {
        if (bottomCard == null) return;
        boolean connected = st != null && st.optBoolean("ok", false);
        String status = st == null ? "idle" : st.optString("status", "idle");
        boolean driverOnline = st != null && st.optBoolean("driverOnline", false);
        boolean hasClient = st != null && st.optBoolean("hasClient", false);
        if ("client".equals(mode)) renderClientPanel(status, driverOnline, connected, st);
        else if ("driver".equals(mode)) renderDriverPanel(status, hasClient, connected, st);
        else renderAdminPanel(status, driverOnline, hasClient, connected, st);
    }

    private void renderClientPanel(String status, boolean driverOnline, boolean connected, JSONObject st) {
        bottomTitle.setText("Solicitação executiva");
        if (!connected) {
            bottomText.setText("Localização pronta. Para teste em dois aparelhos, inicie a Central/Admin em um celular e informe o IP aqui. Para teste local, toque em solicitar e o app inicia um servidor interno.");
        } else if ("idle".equals(status)) {
            bottomText.setText(driverOnline ? "Motorista disponível próximo. Informe o destino na apresentação ou solicite neste local." : "Nenhum motorista online no momento. Peça ao motorista para ficar online ou use a Central/Admin.");
        } else {
            bottomText.setText(statusPt(status, st));
        }
        primaryBtn.setText("idle".equals(status) || "driver_online".equals(status) || "cancelled".equals(status) || "finished".equals(status) ? "Solicitar transporte neste local" : "Atualizar acompanhamento");
        primaryBtn.setOnClickListener(v -> { if ("idle".equals(status) || "driver_online".equals(status) || "cancelled".equals(status) || "finished".equals(status)) requestRide(); else fetchState(); });
        secondaryBtn.setVisibility(View.VISIBLE); secondaryBtn.setText("Destino demonstrativo / Para onde?"); secondaryBtn.setOnClickListener(v -> showDestinationDialog());
        thirdBtn.setVisibility(View.VISIBLE); thirdBtn.setText("Configurar servidor LAN"); thirdBtn.setOnClickListener(v -> showLanDialog());
        if (!driverOnline) showOrHideDriver(false, 0, 0, null);
    }

    private void renderDriverPanel(String status, boolean hasClient, boolean connected, JSONObject st) {
        bottomTitle.setText("Área do motorista");
        String clean = driverOnlineLocal ? "Você está online e visível para clientes próximos." : "Você está offline. Toque em ficar online para aparecer no mapa do cliente.";
        if (connected && hasClient && ("requested".equals(status))) clean = "Nova solicitação recebida. Verifique o ponto do cliente no mapa.";
        else if (connected && "accepted".equals(status)) clean = "Atendimento aceito. Siga até o cliente e informe a chegada.";
        else if (connected && "arrived".equals(status)) clean = "Chegada informada ao cliente.";
        bottomText.setText(clean);
        primaryBtn.setText(driverOnlineLocal ? "Ficar offline" : "Ficar online");
        primaryBtn.setOnClickListener(v -> toggleDriverOnline());
        secondaryBtn.setVisibility(View.VISIBLE);
        if ("requested".equals(status)) { secondaryBtn.setText("Aceitar solicitação"); secondaryBtn.setOnClickListener(v -> sendAction("accept")); }
        else if ("accepted".equals(status)) { secondaryBtn.setText("Informar chegada"); secondaryBtn.setOnClickListener(v -> sendAction("arrived")); }
        else if ("arrived".equals(status)) { secondaryBtn.setText("Iniciar atendimento"); secondaryBtn.setOnClickListener(v -> sendAction("start")); }
        else if ("started".equals(status)) { secondaryBtn.setText("Finalizar atendimento"); secondaryBtn.setOnClickListener(v -> sendAction("finish")); }
        else { secondaryBtn.setText("Perfil do veículo"); secondaryBtn.setOnClickListener(v -> showVehicleDialog()); }
        thirdBtn.setVisibility(View.VISIBLE); thirdBtn.setText("Configurar servidor LAN"); thirdBtn.setOnClickListener(v -> showLanDialog());
    }

    private void renderAdminPanel(String status, boolean driverOnline, boolean hasClient, boolean connected, JSONObject st) {
        bottomTitle.setText("Central demonstrativa");
        String ip = getLocalIp();
        String txt = (LocalHubServer.isRunning() ? "Servidor local ativo. IP para outros aparelhos: "+ip+":8080" : "Servidor local ainda não iniciado.") +
                "\nStatus: " + statusPtShort(status) +
                "\nMotorista: " + (driverOnline ? "online" : "offline") +
                " • Cliente: " + (hasClient ? "solicitou" : "aguardando");
        bottomText.setText(txt);
        primaryBtn.setText(LocalHubServer.isRunning() ? "Servidor local ativo" : "Iniciar servidor local da demo");
        primaryBtn.setOnClickListener(v -> startLocalServer(true));
        secondaryBtn.setVisibility(View.VISIBLE); secondaryBtn.setText("Atualizar painel"); secondaryBtn.setOnClickListener(v -> fetchState());
        thirdBtn.setVisibility(View.VISIBLE); thirdBtn.setText("Limpar corrida demo"); thirdBtn.setOnClickListener(v -> sendAction("reset"));
    }

    private void requestLocation() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 12);
            return;
        }
        beginLocationUpdates();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        beginLocationUpdates();
    }

    private void beginLocationUpdates() {
        try {
            locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
            Location best = null;
            for (String p : locationManager.getProviders(true)) {
                try {
                    Location l = locationManager.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getAccuracy() < best.getAccuracy())) best = l;
                    locationManager.requestLocationUpdates(p, 1800, 1.5f, this);
                } catch (SecurityException ignored) { }
            }
            if (best != null) onLocationChanged(best);
            else updateTop("Aguardando GPS/localização...", friendlyConnection(null));
        } catch (Exception e) {
            updateTop("Localização indisponível", "Ative a localização do aparelho para testar o mapa real.");
        }
    }

    @Override public void onLocationChanged(Location loc) {
        myLat = loc.getLatitude(); myLng = loc.getLongitude(); myAccuracy = loc.getAccuracy(); hasLocation = true;
        if (!centeredOnce) { centeredOnce = true; centerOnMe(); }
        updateTop("Localização real encontrada • precisão aprox. " + Math.round(Math.max(0, myAccuracy)) + " m", friendlyConnection(lastState));
        updateMeMarker();
        if ("client".equals(mode)) sendLocation("clientLocation");
        if ("driver".equals(mode) && driverOnlineLocal) sendDriverOnline();
    }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }

    private void updateTop(String subtitle, String small) {
        if (topSubtitle != null) topSubtitle.setText(subtitle);
        if (topSmall != null) topSmall.setText(small == null ? "" : small);
    }

    private String friendlyConnection(JSONObject st) {
        if (st != null && st.optBoolean("ok", false)) return "Conectado à central da demo";
        if (LocalHubServer.isRunning()) return "Servidor local ativo neste aparelho";
        return "Central LAN não conectada";
    }

    private void updateMeMarker() {
        if (map == null || !hasLocation) return;
        if (meMarker == null) { meMarker = new Marker(map); meMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); meMarker.setIcon(circleIcon(Color.rgb(116, 230, 170), dp(18))); meMarker.setTitle("Você está aqui"); map.getOverlays().add(meMarker); }
        meMarker.setPosition(new GeoPoint(myLat, myLng));
        map.invalidate();
    }

    private void showOrHideDriver(boolean show, double lat, double lng, JSONObject st) {
        if (map == null) return;
        if (!show) { if (driverMarker != null) { map.getOverlays().remove(driverMarker); driverMarker = null; map.invalidate(); } return; }
        if (driverMarker == null) { driverMarker = new Marker(map); driverMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); driverMarker.setIcon(carIcon(dp(52), st)); map.getOverlays().add(driverMarker); }
        driverMarker.setPosition(new GeoPoint(lat, lng));
        driverMarker.setTitle(st == null ? vehicleModel : st.optString("vehicleModel", vehicleModel));
        map.invalidate();
    }

    private void showOrHideClient(boolean show, double lat, double lng) {
        if (map == null) return;
        if (!show) { if (clientMarker != null) { map.getOverlays().remove(clientMarker); clientMarker = null; map.invalidate(); } return; }
        if (clientMarker == null) { clientMarker = new Marker(map); clientMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); clientMarker.setIcon(circleIcon(Color.rgb(38, 116, 255), dp(22))); clientMarker.setTitle("Cliente"); map.getOverlays().add(clientMarker); }
        clientMarker.setPosition(new GeoPoint(lat, lng)); map.invalidate();
    }

    private void centerOnMe() {
        if (map == null) return;
        GeoPoint p = new GeoPoint(hasLocation ? myLat : FALLBACK_LAT, hasLocation ? myLng : FALLBACK_LNG);
        map.getController().animateTo(p);
        map.getController().setZoom(hasLocation ? 18.2 : 16.5);
    }

    private void cycleMapMode() {
        if ("hd".equals(mapMode)) mapMode = "osm";
        else if ("osm".equals(mapMode)) mapMode = "dark";
        else mapMode = "hd";
        prefs.edit().putString("mapMode", mapMode).apply();
        applyMapSource();
        Toast.makeText(this, "Mapa: " + ("hd".equals(mapMode) ? "HD limpo" : "osm".equals(mapMode) ? "Padrão" : "Escuro"), Toast.LENGTH_SHORT).show();
    }

    private void applyMapSource() {
        if (map == null) return;
        if ("dark".equals(mapMode)) map.setTileSource(TILE_DARK_HD);
        else if ("osm".equals(mapMode)) map.setTileSource(TileSourceFactory.MAPNIK);
        else map.setTileSource(TILE_CARTO_HD);
        map.setTilesScaledToDpi(true);
        map.invalidate();
    }

    private void requestRide() {
        if (isLocalhost(serverUrl) && !LocalHubServer.isRunning()) startLocalServer(false);
        sendLocation("clientRequest");
        Toast.makeText(this, "Solicitação enviada para a central da demo", Toast.LENGTH_SHORT).show();
        handler.postDelayed(this::fetchState, 700);
    }

    private void toggleDriverOnline() {
        if (isLocalhost(serverUrl) && !LocalHubServer.isRunning()) startLocalServer(false);
        driverOnlineLocal = !driverOnlineLocal;
        if (driverOnlineLocal) sendDriverOnline(); else sendAction("driverOffline");
        renderBottom(lastState);
    }

    private void sendDriverOnline() {
        if (isLocalhost(serverUrl) && !LocalHubServer.isRunning()) startLocalServer(false);
        String url = serverUrl + "/driverOnline?lat="+myLat+"&lng="+myLng+"&name="+enc(driverName)+"&model="+enc(vehicleModel)+"&color="+enc(vehicleColor)+"&plate="+enc(vehiclePlate);
        http(url, false);
    }

    private void sendLocation(String endpoint) {
        if (!hasLocation) { Toast.makeText(this, "Aguardando localização real do aparelho", Toast.LENGTH_SHORT).show(); return; }
        http(serverUrl + "/" + endpoint + "?lat=" + myLat + "&lng=" + myLng, false);
    }

    private void sendAction(String action) { if (isLocalhost(serverUrl) && !LocalHubServer.isRunning()) startLocalServer(false); http(serverUrl + "/" + action, true); }

    private void fetchState() { http(serverUrl + "/state", true); }

    private void http(String url, boolean showErrors) {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(1800); c.setReadTimeout(1800); c.setRequestMethod("GET");
                InputStream in = c.getInputStream();
                String body = readAll(in);
                JSONObject st = new JSONObject(body);
                runOnUiThread(() -> applyState(st));
            } catch (Exception e) {
                if (showErrors) runOnUiThread(() -> Toast.makeText(this, "Central LAN não conectada", Toast.LENGTH_SHORT).show());
                runOnUiThread(() -> { lastState = null; updateTop(hasLocation ? "Localização real encontrada • precisão aprox. " + Math.round(Math.max(0, myAccuracy)) + " m" : "Buscando localização real...", friendlyConnection(null)); renderBottom(null); });
            }
        }).start();
    }

    private void applyState(JSONObject st) {
        lastState = st;
        boolean ok = st.optBoolean("ok", false);
        updateTop(hasLocation ? "Localização real encontrada • precisão aprox. " + Math.round(Math.max(0, myAccuracy)) + " m" : "Buscando localização real...", friendlyConnection(st));
        if (!ok) { renderBottom(st); return; }
        boolean driverOnline = st.optBoolean("driverOnline", false);
        boolean hasClient = st.optBoolean("hasClient", false);
        if ("client".equals(mode)) {
            showOrHideDriver(driverOnline, st.optDouble("driverLat", FALLBACK_LAT), st.optDouble("driverLng", FALLBACK_LNG), st);
        } else if ("driver".equals(mode)) {
            showOrHideClient(hasClient, st.optDouble("clientLat", FALLBACK_LAT), st.optDouble("clientLng", FALLBACK_LNG));
            showOrHideDriver(true, hasLocation ? myLat : st.optDouble("driverLat", FALLBACK_LAT), hasLocation ? myLng : st.optDouble("driverLng", FALLBACK_LNG), st);
        } else if ("admin".equals(mode)) {
            showOrHideClient(hasClient, st.optDouble("clientLat", FALLBACK_LAT), st.optDouble("clientLng", FALLBACK_LNG));
            showOrHideDriver(driverOnline, st.optDouble("driverLat", FALLBACK_LAT), st.optDouble("driverLng", FALLBACK_LNG), st);
        }
        renderBottom(st);
    }

    private void startTick() {
        stopTick();
        tick = () -> {
            if (!"home".equals(mode) && !"privacy".equals(mode)) fetchState();
            handler.postDelayed(tick, 2600);
        };
        handler.postDelayed(tick, 900);
    }
    private void stopTick() { if (tick != null) handler.removeCallbacks(tick); }

    private void startLocalServer(boolean showToast) {
        try {
            LocalHubServer.start();
            serverUrl = "http://127.0.0.1:8080";
            prefs.edit().putString("serverUrl", serverUrl).apply();
            if (showToast) Toast.makeText(this, "Servidor local ativo. IP: " + getLocalIp() + ":8080", Toast.LENGTH_LONG).show();
            fetchState();
        } catch (Exception e) { Toast.makeText(this, "Não foi possível iniciar servidor local", Toast.LENGTH_LONG).show(); }
    }

    private void showLanDialog() {
        final EditText input = new EditText(this); input.setSingleLine(true); input.setText(serverUrl); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.LTGRAY); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); int p = dp(18); box.setPadding(p, p, p, p);
        TextView info = text("Para dois aparelhos: inicie a Central/Admin em um celular e coloque aqui http://IP_DA_CENTRAL:8080.\nIP deste aparelho: " + getLocalIp(), 14, Color.WHITE, false);
        box.addView(info); box.addView(input);
        new AlertDialog.Builder(this).setTitle("Servidor LAN da demo").setView(box).setPositiveButton("Salvar", (d,w) -> { serverUrl = input.getText().toString().trim(); if (!serverUrl.startsWith("http")) serverUrl = "http://"+serverUrl; prefs.edit().putString("serverUrl", serverUrl).apply(); fetchState(); }).setNegativeButton("Cancelar", null).setNeutralButton("Usar local", (d,w) -> { startLocalServer(true); }).show();
    }

    private void showVehicleDialog() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18), dp(6), dp(18), dp(6));
        EditText n = field(driverName); EditText m = field(vehicleModel); EditText c = field(vehicleColor); EditText p = field(vehiclePlate);
        box.addView(label("Nome do motorista")); box.addView(n); box.addView(label("Modelo / categoria")); box.addView(m); box.addView(label("Cor")); box.addView(c); box.addView(label("Placa")); box.addView(p);
        new AlertDialog.Builder(this).setTitle("Perfil do veículo").setView(box).setPositiveButton("Salvar", (d,w) -> {
            driverName = n.getText().toString(); vehicleModel = m.getText().toString(); vehicleColor = c.getText().toString(); vehiclePlate = p.getText().toString();
            prefs.edit().putString("driverName", driverName).putString("vehicleModel", vehicleModel).putString("vehicleColor", vehicleColor).putString("vehiclePlate", vehiclePlate).apply();
            if (driverMarker != null) { map.getOverlays().remove(driverMarker); driverMarker = null; }
            sendDriverOnline();
        }).setNegativeButton("Cancelar", null).show();
    }

    private void showDestinationDialog() {
        final EditText input = field(""); input.setHint("Ex.: Rodoviária, hospital, hotel, bairro...");
        new AlertDialog.Builder(this).setTitle("Destino demonstrativo").setMessage("Na demo v0.1.2, o destino é visual/informativo. A busca real de endereços entra nas próximas versões.").setView(input).setPositiveButton("Salvar", (d,w)-> Toast.makeText(this, "Destino registrado para apresentação", Toast.LENGTH_SHORT).show()).setNegativeButton("Cancelar", null).show();
    }

    private String statusPt(String s, JSONObject st) {
        if ("requested".equals(s)) return "Solicitação enviada. Aguardando confirmação do motorista.";
        if ("accepted".equals(s)) return "Motorista confirmado: " + st.optString("driverName", driverName) + " • " + st.optString("vehicleModel", vehicleModel) + " • " + st.optString("vehiclePlate", vehiclePlate);
        if ("arrived".equals(s)) return "Seu motorista chegou ao local de embarque.";
        if ("started".equals(s)) return "Atendimento iniciado.";
        if ("finished".equals(s)) return "Atendimento finalizado.";
        if ("cancelled".equals(s)) return "Solicitação cancelada.";
        if ("driver_online".equals(s)) return "Motorista disponível próximo.";
        return "Localização pronta. O mapa já foi centralizado no seu ponto real.";
    }
    private String statusPtShort(String s) {
        if ("requested".equals(s)) return "solicitação enviada"; if ("accepted".equals(s)) return "motorista confirmado"; if ("arrived".equals(s)) return "motorista chegou"; if ("started".equals(s)) return "atendimento iniciado"; if ("finished".equals(s)) return "finalizado"; if ("cancelled".equals(s)) return "cancelado"; if ("driver_online".equals(s)) return "motorista online"; return "aguardando";
    }

    private Button miniButton(String s, View.OnClickListener l) { Button b = new Button(this); b.setAllCaps(false); b.setText(s); b.setTextColor(WHITE); b.setTextSize(13); b.setBackground(round(Color.BLACK, dp(23), GOLD, 1)); b.setOnClickListener(l); return b; }
    private LinearLayout.LayoutParams miniLp() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1); lp.setMargins(dp(4),0,dp(4),0); return lp; }
    private Button bigButton(String s, boolean gold) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(16); b.setTypeface(Typeface.DEFAULT_BOLD); b.setTextColor(gold ? Color.BLACK : WHITE); b.setBackground(gold ? goldBg() : strokeBg()); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(62)); lp.setMargins(0, dp(7), 0, dp(3)); b.setLayoutParams(lp); return b; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); v.setIncludeFontPadding(true); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private TextView label(String s) { TextView v = text(s, 13, Color.DKGRAY, true); v.setPadding(0, dp(8), 0, 0); return v; }
    private EditText field(String s) { EditText e = new EditText(this); e.setSingleLine(true); e.setText(s); e.setSelectAllOnFocus(false); return e; }
    private void addSpace(LinearLayout l, int h) { Space s = new Space(this); l.addView(s, new LinearLayout.LayoutParams(1, dp(h))); }
    private Drawable goldBg() { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{GOLD2, Color.rgb(183,125,31)}); g.setCornerRadius(dp(18)); return g; }
    private Drawable strokeBg() { return round(Color.rgb(12,12,12), dp(18), Color.rgb(112,76,24), 1); }
    private Drawable round(int color, int r, int stroke, int sw) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(r); if (sw > 0) g.setStroke(dp(sw), stroke); return g; }
    private Drawable circleIcon(int color, int size) { Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(b); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.WHITE); c.drawCircle(size/2f, size/2f, size/2f, p); p.setColor(color); c.drawCircle(size/2f, size/2f, size*.36f, p); p.setColor(GOLD); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2, size*.08f)); c.drawCircle(size/2f, size/2f, size*.44f, p); return new BitmapDrawable(getResources(), b); }
    private Drawable carIcon(int size, JSONObject st) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(b); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = size/2f, cy = size/2f;
        p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(218, 13, 13, 13)); c.drawCircle(cx, cy, size*.45f, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(size*.055f); p.setColor(GOLD); c.drawCircle(cx, cy, size*.43f, p);
        p.setStyle(Paint.Style.FILL); p.setColor(GOLD2); RectF body = new RectF(size*.24f, size*.30f, size*.76f, size*.76f); c.drawRoundRect(body, size*.12f, size*.12f, p);
        p.setColor(Color.rgb(8,8,8)); RectF roof = new RectF(size*.34f, size*.22f, size*.66f, size*.42f); c.drawRoundRect(roof, size*.09f, size*.09f, p);
        p.setColor(Color.WHITE); c.drawCircle(size*.33f, size*.72f, size*.07f, p); c.drawCircle(size*.67f, size*.72f, size*.07f, p);
        p.setColor(Color.argb(90,255,255,255)); c.drawRoundRect(new RectF(size*.28f, size*.36f, size*.72f, size*.58f), size*.08f, size*.08f, p);
        return new BitmapDrawable(getResources(), b);
    }
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private boolean isLocalhost(String u) { return u == null || u.contains("127.0.0.1") || u.contains("localhost"); }
    private String enc(String s) { try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); } catch (Exception e) { return ""; } }
    private String readAll(InputStream in) throws IOException { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n; while ((n=in.read(buf))>=0) out.write(buf,0,n); return out.toString("UTF-8"); }
    private String getLocalIp() {
        try { Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); while (en.hasMoreElements()) { NetworkInterface intf = en.nextElement(); Enumeration<InetAddress> addrs = intf.getInetAddresses(); while (addrs.hasMoreElements()) { InetAddress a = addrs.nextElement(); if (!a.isLoopbackAddress() && a instanceof Inet4Address) return a.getHostAddress(); } } } catch (Exception ignored) { }
        return "IP indisponível";
    }

    @Override protected void onResume() { super.onResume(); if (map != null) map.onResume(); }
    @Override protected void onPause() { super.onPause(); if (map != null) map.onPause(); }
}
