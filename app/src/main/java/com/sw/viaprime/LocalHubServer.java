package com.sw.viaprime;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;

public class LocalHubServer {
    public static final int PORT = 8080;
    private static volatile boolean running = false;
    private static ServerSocket serverSocket;
    private static Thread serverThread;

    private static final Object lock = new Object();
    private static double clientLat = -16.94155;
    private static double clientLng = -50.44485;
    private static double driverLat = -16.94120;
    private static double driverLng = -50.44450;
    private static boolean hasClient = false;
    private static boolean driverOnline = false;
    private static String status = "idle";
    private static String driverName = "Motorista demo";
    private static String vehicleModel = "Sedan executivo";
    private static String vehicleColor = "Preto";
    private static String vehiclePlate = "VP-0001";
    private static long updatedAt = System.currentTimeMillis();

    public static boolean isRunning() { return running; }

    public static void start() throws IOException {
        if (running) return;
        serverSocket = new ServerSocket(PORT);
        running = true;
        serverThread = new Thread(() -> {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    handle(socket);
                } catch (Exception ignored) { }
            }
        }, "ViaPrimeLocalHub");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public static void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) { }
    }

    private static void handle(Socket socket) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line = br.readLine();
            if (line == null) { socket.close(); return; }
            String[] parts = line.split(" ");
            String target = parts.length > 1 ? parts[1] : "/state";
            while ((line = br.readLine()) != null && line.length() > 0) { }
            String response = route(target);
            byte[] data = response.getBytes("UTF-8");
            OutputStream out = socket.getOutputStream();
            out.write(("HTTP/1.1 200 OK\r\n"+
                    "Content-Type: application/json; charset=utf-8\r\n"+
                    "Access-Control-Allow-Origin: *\r\n"+
                    "Cache-Control: no-store\r\n"+
                    "Content-Length: "+data.length+"\r\n"+
                    "Connection: close\r\n\r\n").getBytes("UTF-8"));
            out.write(data);
            out.flush();
        } catch (Exception ignored) {
        } finally {
            try { socket.close(); } catch (Exception ignored) { }
        }
    }

    private static String route(String target) {
        try {
            URI uri = new URI(target);
            String path = uri.getPath();
            Map<String, String> q = query(uri.getRawQuery());
            synchronized (lock) {
                if ("/reset".equals(path)) {
                    hasClient = false; driverOnline = false; status = "idle";
                } else if ("/clientLocation".equals(path)) {
                    clientLat = d(q.get("lat"), clientLat); clientLng = d(q.get("lng"), clientLng); hasClient = true;
                } else if ("/driverLocation".equals(path)) {
                    driverLat = d(q.get("lat"), driverLat); driverLng = d(q.get("lng"), driverLng);
                } else if ("/driverOnline".equals(path)) {
                    driverOnline = true;
                    driverLat = d(q.get("lat"), driverLat); driverLng = d(q.get("lng"), driverLng);
                    driverName = s(q.get("name"), driverName);
                    vehicleModel = s(q.get("model"), vehicleModel);
                    vehicleColor = s(q.get("color"), vehicleColor);
                    vehiclePlate = s(q.get("plate"), vehiclePlate);
                    if ("idle".equals(status) || "cancelled".equals(status) || "finished".equals(status)) status = "driver_online";
                } else if ("/driverOffline".equals(path)) {
                    driverOnline = false;
                    if ("driver_online".equals(status)) status = "idle";
                } else if ("/clientRequest".equals(path)) {
                    clientLat = d(q.get("lat"), clientLat); clientLng = d(q.get("lng"), clientLng); hasClient = true; status = "requested";
                } else if ("/accept".equals(path)) {
                    status = "accepted";
                } else if ("/arrived".equals(path)) {
                    status = "arrived";
                } else if ("/start".equals(path)) {
                    status = "started";
                } else if ("/finish".equals(path)) {
                    status = "finished";
                } else if ("/cancel".equals(path)) {
                    status = "cancelled";
                } else if ("/profile".equals(path)) {
                    driverName = s(q.get("name"), driverName);
                    vehicleModel = s(q.get("model"), vehicleModel);
                    vehicleColor = s(q.get("color"), vehicleColor);
                    vehiclePlate = s(q.get("plate"), vehiclePlate);
                }
                updatedAt = System.currentTimeMillis();
            }
            return stateJson();
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\""+esc(e.getMessage())+"\"}";
        }
    }

    private static String stateJson() {
        try {
            JSONObject o = new JSONObject();
            synchronized (lock) {
                o.put("ok", true);
                o.put("status", status);
                o.put("clientLat", clientLat);
                o.put("clientLng", clientLng);
                o.put("driverLat", driverLat);
                o.put("driverLng", driverLng);
                o.put("hasClient", hasClient);
                o.put("driverOnline", driverOnline);
                o.put("driverName", driverName);
                o.put("vehicleModel", vehicleModel);
                o.put("vehicleColor", vehicleColor);
                o.put("vehiclePlate", vehiclePlate);
                o.put("updatedAt", updatedAt);
            }
            return o.toString();
        } catch (Exception e) {
            return "{\"ok\":false}";
        }
    }

    private static Map<String, String> query(String raw) throws UnsupportedEncodingException {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String p : raw.split("&")) {
            int i = p.indexOf('=');
            String k = i >= 0 ? p.substring(0, i) : p;
            String v = i >= 0 ? p.substring(i + 1) : "";
            map.put(URLDecoder.decode(k, "UTF-8"), URLDecoder.decode(v, "UTF-8"));
        }
        return map;
    }
    private static double d(String v, double def) { try { return v == null ? def : Double.parseDouble(v); } catch (Exception e) { return def; } }
    private static String s(String v, String def) { return v == null || v.trim().isEmpty() ? def : v.trim(); }
    private static String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }
}
