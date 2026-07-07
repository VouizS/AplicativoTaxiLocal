package com.sw.viaprime;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int GOLD = Color.rgb(214, 170, 76);
    private static final int GOLD_DARK = Color.rgb(152, 106, 32);
    private static final int BG = Color.rgb(5, 5, 5);
    private static final int CARD = Color.rgb(17, 17, 17);
    private static final int TEXT = Color.rgb(245, 238, 220);
    private static final int MUTED = Color.rgb(185, 178, 160);
    private static final int REQ_LOCATION = 2001;
    private static final int REQ_NOTIFY = 2002;
    private static LocalHubServer sharedServer;

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FusedLocationProviderClient fused;
    private LocationCallback locationCallback;
    private MapView map;
    private LinearLayout bottomPanel;
    private TextView topStatus;
    private TextView serverLabel;
    private Marker meMarker, clientMarker, driverMarker;
    private GeoPoint myPoint;
    private boolean firstFix = true;
    private boolean driverOnline = false;
    private boolean clientHasRide = false;
    private String mode = "";
    private String lastStatus = "";
    private JSONObject state = new JSONObject();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("vp_demo", MODE_PRIVATE);
        Configuration.getInstance().load(this, prefs);
        Configuration.getInstance().setUserAgentValue("com.sw.viaprime.demo/0.1.0");
        fused = LocationServices.getFusedLocationProviderClient(this);
        askNotificationsIfNeeded();
        showProfilePicker();
    }

    private void showProfilePicker() {
        stopLocationUpdates();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(30), dp(24), dp(30));
        root.setBackgroundColor(BG);

        TextView crown = label("♛", 46, GOLD, true); crown.setGravity(Gravity.CENTER);
        TextView title = label("VIA PRIME", 42, GOLD, true); title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.08f);
        TextView sub = label("TRANSPORTE EXECUTIVO", 15, TEXT, false); sub.setGravity(Gravity.CENTER); sub.setLetterSpacing(0.18f);
        TextView demo = label("Protótipo funcional v0.1 • Mapa real • Localização real • Servidor LAN", 13, MUTED, false); demo.setGravity(Gravity.CENTER); demo.setPadding(0, dp(16), 0, dp(22));
        root.addView(crown); root.addView(title); root.addView(sub); root.addView(demo);
        root.addView(button("Entrar como Cliente", v -> openMode("cliente"), true));
        root.addView(space(10));
        root.addView(button("Entrar como Motorista", v -> openMode("motorista"), true));
        root.addView(space(10));
        root.addView(button("Entrar como Central/Admin", v -> openMode("central"), false));
        root.addView(space(18));
        root.addView(button("Privacidade e permissões", v -> showPrivacy(), false));
        setContentView(root);
    }

    private void openMode(String newMode) {
        mode = newMode;
        firstFix = true;
        setupMapScreen();
        startLocationUpdates();
        startPolling();
        if ("central".equals(mode)) rebuildCentralPanel();
        if ("motorista".equals(mode)) { ensureVehicleDefaults(); rebuildDriverPanel(); }
        if ("cliente".equals(mode)) rebuildClientPanel();
    }

    private void setupMapScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        map = new MapView(this);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true);
        map.getController().setZoom(16.0);
        map.getController().setCenter(new GeoPoint(-16.947, -50.448));
        root.addView(map, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(14), dp(10), dp(14), dp(10));
        top.setBackground(cardBg(18, true));
        TextView t = label(modeTitle(), 18, GOLD, true);
        topStatus = label("Buscando localização real...", 12, TEXT, false);
        serverLabel = label(serverText(), 11, MUTED, false);
        top.addView(t); top.addView(topStatus); top.addView(serverLabel);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        topLp.setMargins(dp(12), dp(12), dp(12), 0);
        root.addView(top, topLp);

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(Gravity.CENTER);
        quick.setPadding(dp(8), 0, dp(8), 0);
        quick.addView(miniButton("Menu", v -> showProfilePicker()));
        quick.addView(miniButton("LAN", v -> showServerDialog()));
        quick.addView(miniButton("Centro", v -> centerOnMyLocation()));
        if ("central".equals(mode)) quick.addView(miniButton("Servidor", v -> startServerAndRefresh()));
        FrameLayout.LayoutParams qlp = new FrameLayout.LayoutParams(-2, dp(44), Gravity.TOP | Gravity.RIGHT);
        qlp.setMargins(0, dp(96), dp(12), 0);
        root.addView(quick, qlp);

        bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setPadding(dp(16), dp(14), dp(16), dp(16));
        bottomPanel.setBackground(cardBg(22, true));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bp.setMargins(dp(12), 0, dp(12), dp(12));
        root.addView(bottomPanel, bp);
        setContentView(root);
    }

    private String modeTitle() {
        if ("cliente".equals(mode)) return "Via Prime Cliente";
        if ("motorista".equals(mode)) return "Via Prime Motoristas";
        return "Via Prime Central";
    }

    private void rebuildClientPanel() {
        if (bottomPanel == null) return;
        bottomPanel.removeAllViews();
        bottomPanel.addView(label("Solicitação executiva", 18, GOLD, true));
        bottomPanel.addView(label(clientStatusText(), 13, TEXT, false));
        bottomPanel.addView(space(8));
        if (!clientHasRide && !hasRideRequested()) {
            bottomPanel.addView(button("Solicitar transporte neste local", v -> requestRide(), true));
        } else {
            bottomPanel.addView(button("Atualizar acompanhamento", v -> fetchStateNow(), false));
            bottomPanel.addView(space(8));
            bottomPanel.addView(button("Cancelar solicitação", v -> postSimple("/api/cancel", true), false));
        }
        bottomPanel.addView(space(8));
        bottomPanel.addView(button("Configurar servidor LAN", v -> showServerDialog(), false));
    }

    private void rebuildDriverPanel() {
        if (bottomPanel == null) return;
        bottomPanel.removeAllViews();
        bottomPanel.addView(label("Área do motorista", 18, GOLD, true));
        bottomPanel.addView(label(driverStatusText(), 13, TEXT, false));
        bottomPanel.addView(space(8));
        bottomPanel.addView(button(driverOnline ? "Ficar offline" : "Ficar online", v -> toggleDriverOnline(), true));
        bottomPanel.addView(space(8));
        if (isStatus("requested")) bottomPanel.addView(button("Aceitar atendimento", v -> acceptRide(), true));
        if (isStatus("accepted") || isStatus("on_way")) {
            bottomPanel.addView(button("Abrir rota até o cliente", v -> openRouteToClient(), false));
            bottomPanel.addView(space(8));
            bottomPanel.addView(button("Cheguei ao local", v -> postSimple("/api/arrived", true), true));
        }
        if (isStatus("arrived")) bottomPanel.addView(button("Iniciar atendimento", v -> postSimple("/api/start", true), true));
        if (isStatus("started")) bottomPanel.addView(button("Finalizar atendimento", v -> postSimple("/api/finish", true), true));
        bottomPanel.addView(space(8));
        bottomPanel.addView(button("Perfil do veículo", v -> showVehicleDialog(), false));
        bottomPanel.addView(space(8));
        bottomPanel.addView(button("Configurar servidor LAN", v -> showServerDialog(), false));
    }

    private void rebuildCentralPanel() {
        if (bottomPanel == null) return;
        bottomPanel.removeAllViews();
        bottomPanel.addView(label("Central demonstrativa", 18, GOLD, true));
        bottomPanel.addView(label(serverText(), 12, MUTED, false));
        bottomPanel.addView(label(centralStatusText(), 13, TEXT, false));
        bottomPanel.addView(space(8));
        bottomPanel.addView(button("Iniciar servidor local da demo", v -> startServerAndRefresh(), true));
        bottomPanel.addView(space(8));
        bottomPanel.addView(button("Atualizar painel", v -> fetchStateNow(), false));
        bottomPanel.addView(space(8));
        bottomPanel.addView(button("Limpar corrida demo", v -> postSimple("/api/reset", true), false));
    }

    private String clientStatusText() {
        String st = opt("status");
        if (myPoint == null) return "Permita a localização para o app focar exatamente onde você está.";
        if (st.length() == 0) return "Localização pronta. O mapa já foi centralizado no seu ponto real.";
        if ("requested".equals(st)) return "Procurando motorista disponível...";
        if ("accepted".equals(st)) return "Motorista confirmado. Acompanhamento iniciado.";
        if ("on_way".equals(st)) return driverSummary() + " está a caminho.";
        if ("arrived".equals(st)) return "Seu motorista chegou ao local de embarque.";
        if ("started".equals(st)) return "Atendimento iniciado.";
        if ("finished".equals(st)) return "Atendimento finalizado.";
        if ("cancelled".equals(st)) return "Solicitação cancelada.";
        return "Status: " + st;
    }

    private String driverStatusText() {
        String base = driverOnline ? "Você está online. " : "Você está offline. ";
        String st = opt("status");
        if ("requested".equals(st)) return base + "Nova solicitação disponível no mapa.";
        if ("accepted".equals(st) || "on_way".equals(st)) return base + "Cliente no mapa. Use a rota ou marque chegada.";
        if ("arrived".equals(st)) return base + "Chegada informada ao cliente.";
        if ("started".equals(st)) return base + "Atendimento em andamento.";
        return base + "Servidor: " + serverText();
    }

    private String centralStatusText() {
        String st = opt("status");
        StringBuilder sb = new StringBuilder();
        sb.append("Status: ").append(st.length() == 0 ? "sem corrida ativa" : st).append("\n");
        if (optDouble("clientLat") != 0) sb.append("Cliente: ").append(fmt(optDouble("clientLat"))).append(", ").append(fmt(optDouble("clientLon"))).append("\n");
        if (optDouble("driverLat") != 0) sb.append("Motorista: ").append(driverSummary()).append("\n");
        return sb.toString();
    }

    private String driverSummary() {
        String name = opt("driverName"); if (name.length() == 0) name = prefs.getString("driverName", "Motorista demo");
        String car = opt("carModel"); if (car.length() == 0) car = prefs.getString("carModel", "Sedan executivo");
        String color = opt("carColor"); if (color.length() == 0) color = prefs.getString("carColor", "Preto");
        String plate = opt("carPlate"); if (plate.length() == 0) plate = prefs.getString("carPlate", "VP-0001");
        return name + " • " + car + " " + color + " • " + plate;
    }

    private void requestRide() {
        if (myPoint == null) { toast("Aguardando localização real do aparelho."); return; }
        ensureLocalServerIfNeeded();
        clientHasRide = true;
        postForm("/api/requestRide", "clientName=Cliente%20demo&lat=" + myPoint.getLatitude() + "&lon=" + myPoint.getLongitude() + "&destination=Destino%20a%20combinar&note=Solicitado%20pelo%20app", () -> {
            toast("Solicitação enviada para a central LAN.");
            rebuildClientPanel();
        });
    }

    private void toggleDriverOnline() {
        driverOnline = !driverOnline;
        if (driverOnline) {
            ensureLocalServerIfNeeded();
            sendDriverLocation("/api/driverOnline", () -> toast("Motorista online."));
        } else toast("Motorista offline nesta tela.");
        rebuildDriverPanel();
    }

    private void acceptRide() {
        ensureLocalServerIfNeeded();
        sendDriverLocation("/api/acceptRide", () -> toast("Atendimento aceito."));
    }

    private void sendDriverLocation(String endpoint, Runnable done) {
        if (myPoint == null) { toast("Aguardando localização real do motorista."); return; }
        String data = "lat=" + myPoint.getLatitude() + "&lon=" + myPoint.getLongitude() +
                "&driverName=" + enc(prefs.getString("driverName", "Motorista demo")) +
                "&carModel=" + enc(prefs.getString("carModel", "Sedan executivo")) +
                "&carColor=" + enc(prefs.getString("carColor", "Preto")) +
                "&carPlate=" + enc(prefs.getString("carPlate", "VP-0001"));
        postForm(endpoint, data, done);
    }

    private void postSimple(String endpoint, boolean refresh) { postForm(endpoint, "ok=1", () -> { if (refresh) fetchStateNow(); }); }

    private void startServerAndRefresh() {
        try {
            if (sharedServer == null) { sharedServer = new LocalHubServer(8080); sharedServer.boot(); }
            prefs.edit().putString("serverUrl", "http://127.0.0.1:8080").apply();
            toast("Servidor local iniciado. IP: " + localIp() + ":8080");
            if (serverLabel != null) serverLabel.setText(serverText());
            rebuildCentralPanel();
        } catch (Exception e) { toast("Falha ao iniciar servidor: " + e.getMessage()); }
    }

    private void ensureLocalServerIfNeeded() {
        String url = prefs.getString("serverUrl", "").trim();
        if (url.length() == 0) startServerAndRefresh();
    }

    private String serverText() {
        String url = prefs.getString("serverUrl", "").trim();
        if (url.length() == 0) return "Servidor LAN não configurado. Use Central/Admin para iniciar ou informe o IP.";
        return "Servidor LAN: " + url + " • IP deste aparelho: " + localIp();
    }

    private void startPolling() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() { @Override public void run() { fetchStateNow(); handler.postDelayed(this, 2500); } }, 1000);
    }

    private void fetchStateNow() {
        String base = prefs.getString("serverUrl", "").trim();
        if (base.length() == 0) return;
        new Thread(() -> {
            try {
                String result = httpGet(base + "/api/state");
                JSONObject json = new JSONObject(result);
                runOnUiThread(() -> applyState(json));
            } catch (Exception ignored) {}
        }).start();
    }

    private void applyState(JSONObject json) {
        state = json;
        String st = opt("status");
        updateStateMarkers();
        fitMapForState();
        if ("arrived".equals(st) && !"arrived".equals(lastStatus) && "cliente".equals(mode)) {
            notifyArrived();
        }
        lastStatus = st;
        if (topStatus != null) topStatus.setText(statusLine());
        if (serverLabel != null) serverLabel.setText(serverText());
        if ("cliente".equals(mode)) rebuildClientPanel();
        if ("motorista".equals(mode)) rebuildDriverPanel();
        if ("central".equals(mode)) rebuildCentralPanel();
    }

    private void updateStateMarkers() {
        double clat = optDouble("clientLat"), clon = optDouble("clientLon");
        double dlat = optDouble("driverLat"), dlon = optDouble("driverLon");
        if (map == null) return;
        if (clat != 0 && clon != 0) clientMarker = putMarker(clientMarker, new GeoPoint(clat, clon), "Cliente", createPinDrawable(GOLD), 0);
        if (dlat != 0 && dlon != 0) driverMarker = putMarker(driverMarker, new GeoPoint(dlat, dlon), "Motorista", createCarDrawable(GOLD), 0);
        map.invalidate();
    }

    private void fitMapForState() {
        if (map == null || myPoint == null) return;
        double clat = optDouble("clientLat"), clon = optDouble("clientLon");
        double dlat = optDouble("driverLat"), dlon = optDouble("driverLon");
        if (clat != 0 && clon != 0 && dlat != 0 && dlon != 0) {
            double lat = (clat + dlat) / 2.0; double lon = (clon + dlon) / 2.0;
            map.getController().animateTo(new GeoPoint(lat, lon));
            map.getController().setZoom(15.5);
        }
    }

    private String statusLine() {
        if (myPoint == null) return "Permissão concedida? aguardando GPS/rede...";
        String st = opt("status");
        if (st.length() == 0) return "Localização real: " + fmt(myPoint.getLatitude()) + ", " + fmt(myPoint.getLongitude());
        return "Status em tempo real: " + st;
    }

    private void startLocationUpdates() {
        if (!hasLocationPermission()) { requestLocationPermission(); return; }
        try {
            LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500).setMinUpdateIntervalMillis(1200).build();
            locationCallback = new LocationCallback() {
                @Override public void onLocationResult(LocationResult result) {
                    if (result == null) return;
                    Location loc = result.getLastLocation();
                    if (loc != null) onNewLocation(loc);
                }
            };
            fused.getLastLocation().addOnSuccessListener(loc -> { if (loc != null) onNewLocation(loc); });
            fused.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) { requestLocationPermission(); }
    }

    private void onNewLocation(Location loc) {
        myPoint = new GeoPoint(loc.getLatitude(), loc.getLongitude());
        if (map != null) {
            if ("motorista".equals(mode)) meMarker = putMarker(meMarker, myPoint, "Meu carro", createCarDrawable(GOLD), loc.getBearing());
            else meMarker = putMarker(meMarker, myPoint, "Você está aqui", createPinDrawable(Color.rgb(80, 160, 255)), 0);
            if (firstFix) { firstFix = false; map.getController().setZoom(16.5); map.getController().animateTo(myPoint); }
            map.invalidate();
        }
        if (topStatus != null) topStatus.setText("Localização real encontrada • precisão aprox. " + Math.round(loc.getAccuracy()) + " m");
        if ("cliente".equals(mode) && clientHasRide) postForm("/api/updateClient", "clientName=Cliente%20demo&lat=" + myPoint.getLatitude() + "&lon=" + myPoint.getLongitude(), null);
        if ("motorista".equals(mode) && driverOnline) sendDriverLocation("/api/updateDriver", null);
    }

    private Marker putMarker(Marker marker, GeoPoint point, String title, android.graphics.drawable.Drawable icon, float rotation) {
        if (marker == null) {
            marker = new Marker(map);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marker.setTitle(title);
            map.getOverlays().add(marker);
        }
        marker.setPosition(point);
        marker.setIcon(icon);
        marker.setRotation(rotation);
        return marker;
    }

    private void centerOnMyLocation() {
        if (map != null && myPoint != null) { map.getController().setZoom(16.5); map.getController().animateTo(myPoint); }
        else toast("Localização ainda não encontrada.");
    }

    private void openRouteToClient() {
        double lat = optDouble("clientLat"), lon = optDouble("clientLon");
        if (lat == 0 || lon == 0) { toast("Cliente ainda não localizado no servidor."); return; }
        Uri uri = Uri.parse("google.navigation:q=" + lat + "," + lon);
        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        try { startActivity(i); } catch (Exception e) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + lat + "," + lon))); }
    }

    private void postForm(String endpoint, String form, Runnable done) {
        String base = prefs.getString("serverUrl", "").trim();
        if (base.length() == 0) { toast("Configure ou inicie o servidor LAN primeiro."); return; }
        new Thread(() -> {
            try {
                String result = httpPost(base + endpoint, form == null ? "" : form);
                JSONObject json = new JSONObject(result);
                runOnUiThread(() -> { applyState(json); if (done != null) done.run(); });
            } catch (Exception e) { runOnUiThread(() -> toast("Falha LAN: " + e.getMessage())); }
        }).start();
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(urlStr).openConnection();
        c.setConnectTimeout(2500); c.setReadTimeout(3500); c.setRequestMethod("GET");
        return read(c);
    }
    private static String httpPost(String urlStr, String form) throws Exception {
        byte[] data = form.getBytes("UTF-8");
        HttpURLConnection c = (HttpURLConnection)new URL(urlStr).openConnection();
        c.setConnectTimeout(2500); c.setReadTimeout(3500); c.setRequestMethod("POST");
        c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        c.setRequestProperty("Content-Length", String.valueOf(data.length));
        DataOutputStream out = new DataOutputStream(c.getOutputStream()); out.write(data); out.flush(); out.close();
        return read(c);
    }
    private static String read(HttpURLConnection c) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String line; while ((line = br.readLine()) != null) sb.append(line); br.close(); return sb.toString();
    }

    private boolean hasRideRequested() { String st = opt("status"); return st.length() > 0 && !"finished".equals(st) && !"cancelled".equals(st); }
    private boolean isStatus(String s) { return s.equals(opt("status")); }
    private String opt(String key) { return state.optString(key, ""); }
    private double optDouble(String key) { return state.optDouble(key, 0); }
    private String fmt(double d) { return String.format(Locale.US, "%.5f", d); }

    private void showServerDialog() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18), dp(10), dp(18), dp(4));
        TextView info = label("Para teste em dois aparelhos: abra Central/Admin em um celular, inicie o servidor e copie o IP exibido. Nos outros aparelhos use: http://IP:8080", 13, Color.DKGRAY, false);
        final EditText input = new EditText(this); input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_TEXT); input.setText(prefs.getString("serverUrl", "")); input.setHint("http://192.168.0.10:8080");
        box.addView(info); box.addView(input);
        new AlertDialog.Builder(this).setTitle("Servidor LAN da demo").setView(box)
                .setPositiveButton("Salvar", (d,w) -> { prefs.edit().putString("serverUrl", input.getText().toString().trim()).apply(); if (serverLabel != null) serverLabel.setText(serverText()); fetchStateNow(); })
                .setNegativeButton("Cancelar", null).show();
    }

    private void showVehicleDialog() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18), 0, dp(18), 0);
        EditText name = edit("Nome do motorista", prefs.getString("driverName", "Motorista demo"));
        EditText model = edit("Modelo do carro", prefs.getString("carModel", "Sedan executivo"));
        EditText color = edit("Cor", prefs.getString("carColor", "Preto"));
        EditText plate = edit("Placa", prefs.getString("carPlate", "VP-0001"));
        box.addView(name); box.addView(model); box.addView(color); box.addView(plate);
        new AlertDialog.Builder(this).setTitle("Perfil do veículo").setView(box)
                .setPositiveButton("Salvar", (d,w) -> { prefs.edit().putString("driverName", name.getText().toString()).putString("carModel", model.getText().toString()).putString("carColor", color.getText().toString()).putString("carPlate", plate.getText().toString()).apply(); rebuildDriverPanel(); })
                .setNegativeButton("Cancelar", null).show();
    }

    private void ensureVehicleDefaults() {
        if (!prefs.contains("driverName")) prefs.edit().putString("driverName", "Motorista demo").putString("carModel", "Sedan executivo").putString("carColor", "Preto").putString("carPlate", "VP-0001").apply();
    }

    private EditText edit(String hint, String value) { EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(true); e.setText(value); return e; }

    private void showPrivacy() {
        new AlertDialog.Builder(this)
                .setTitle("Privacidade da demo")
                .setMessage("Esta v0.1 usa recursos reais e permissões mínimas:\n\nUsa: internet, localização durante uso e notificações locais.\n\nNão usa: SMS, contatos, fotos, câmera, microfone, arquivos, acessibilidade, administrador do dispositivo ou sobreposição.\n\nA localização é usada para demonstrar o ponto do cliente, o carro do motorista e o acompanhamento no mapa.")
                .setPositiveButton("Entendi", null).show();
    }

    private boolean hasLocationPermission() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
    }
    private void askNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
    }
    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) { super.onRequestPermissionsResult(r,p,g); if (r == REQ_LOCATION && hasLocationPermission()) startLocationUpdates(); }

    private void notifyArrived() {
        toast("Seu motorista chegou.");
        try {
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            String ch = "arrivals";
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(ch, "Chegadas", NotificationManager.IMPORTANCE_DEFAULT));
            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(this, 1, intent, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(this, ch) : new android.app.Notification.Builder(this);
            b.setContentTitle("Via Prime").setContentText("Seu motorista chegou ao local de embarque.").setSmallIcon(android.R.drawable.ic_dialog_map).setContentIntent(pi).setAutoCancel(true);
            nm.notify(77, b.build());
        } catch (Exception ignored) {}
    }

    private void stopLocationUpdates() { try { if (fused != null && locationCallback != null) fused.removeLocationUpdates(locationCallback); } catch (Exception ignored) {} }
    @Override protected void onResume() { super.onResume(); if (map != null) map.onResume(); }
    @Override protected void onPause() { if (map != null) map.onPause(); super.onPause(); }
    @Override protected void onDestroy() { stopLocationUpdates(); super.onDestroy(); }

    private TextView label(String s, int sp, int color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); v.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL); v.setLineSpacing(2, 1.05f); return v; }
    private View space(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private Button button(String text, View.OnClickListener l, boolean primary) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(15); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setTextColor(primary ? Color.BLACK : TEXT); b.setBackground(primary ? goldBg() : cardBg(14, true)); b.setOnClickListener(l); b.setPadding(dp(8), dp(10), dp(8), dp(10)); b.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(52))); return b;
    }
    private Button miniButton(String text, View.OnClickListener l) { Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(11); b.setTextColor(TEXT); b.setBackground(cardBg(20, true)); b.setOnClickListener(l); b.setPadding(dp(6),0,dp(6),0); b.setLayoutParams(new LinearLayout.LayoutParams(dp(86), dp(40))); return b; }
    private GradientDrawable cardBg(int radius, boolean stroke) { GradientDrawable g = new GradientDrawable(); g.setColor(CARD); g.setCornerRadius(dp(radius)); if (stroke) g.setStroke(dp(1), GOLD_DARK); return g; }
    private GradientDrawable goldBg() { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.rgb(239, 198, 94), Color.rgb(177, 124, 39)}); g.setCornerRadius(dp(16)); return g; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private BitmapDrawable createPinDrawable(int color) {
        Bitmap bm = Bitmap.createBitmap(dp(64), dp(64), Bitmap.Config.ARGB_8888); Canvas c = new Canvas(bm); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.argb(80, Color.red(color), Color.green(color), Color.blue(color))); c.drawCircle(dp(32), dp(32), dp(26), p);
        p.setColor(color); c.drawCircle(dp(32), dp(32), dp(12), p); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(3)); p.setColor(Color.WHITE); c.drawCircle(dp(32), dp(32), dp(12), p); p.setStyle(Paint.Style.FILL);
        return new BitmapDrawable(getResources(), bm);
    }
    private BitmapDrawable createCarDrawable(int color) {
        Bitmap bm = Bitmap.createBitmap(dp(80), dp(80), Bitmap.Config.ARGB_8888); Canvas c = new Canvas(bm); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.argb(170, 0, 0, 0)); c.drawCircle(dp(40), dp(40), dp(33), p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(3)); p.setColor(color); c.drawCircle(dp(40), dp(40), dp(31), p); p.setStyle(Paint.Style.FILL);
        p.setColor(color); c.drawRoundRect(dp(22), dp(28), dp(58), dp(53), dp(8), dp(8), p);
        p.setColor(Color.BLACK); c.drawRoundRect(dp(28), dp(22), dp(52), dp(36), dp(7), dp(7), p);
        p.setColor(Color.WHITE); c.drawCircle(dp(29), dp(54), dp(4), p); c.drawCircle(dp(51), dp(54), dp(4), p);
        return new BitmapDrawable(getResources(), bm);
    }

    private String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
    private String enc(String v) { try { return URLEncoder.encode(v == null ? "" : v, "UTF-8"); } catch (Exception e) { return ""; } }
}
