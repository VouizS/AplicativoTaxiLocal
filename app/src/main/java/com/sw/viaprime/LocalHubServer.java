package com.sw.viaprime;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import fi.iki.elonen.NanoHTTPD;

public class LocalHubServer extends NanoHTTPD {
    private final Object lock = new Object();
    private final RideState state = new RideState();

    public LocalHubServer(int port) { super(port); }

    public void boot() throws IOException { start(NanoHTTPD.SOCKET_READ_TIMEOUT, false); }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            Method method = session.getMethod();
            if (Method.OPTIONS.equals(method)) return cors(newFixedLengthResponse(Response.Status.OK, "text/plain", "OK"));
            if (Method.POST.equals(method) || Method.PUT.equals(method)) {
                try { session.parseBody(new HashMap<String, String>()); } catch (Exception ignored) {}
            }
            String uri = session.getUri();
            Map<String, List<String>> p = session.getParameters();

            if ("/api/health".equals(uri)) return json("{\"ok\":true,\"service\":\"Via Prime Local Hub\"}");
            if ("/api/state".equals(uri)) return json(toJson());

            synchronized (lock) {
                if ("/api/requestRide".equals(uri)) {
                    state.rideId = UUID.randomUUID().toString();
                    state.status = "requested";
                    state.clientName = val(p, "clientName", "Cliente demo");
                    state.clientLat = dbl(p, "lat", 0);
                    state.clientLon = dbl(p, "lon", 0);
                    state.destination = val(p, "destination", "Destino não informado");
                    state.note = val(p, "note", "");
                    state.requestedAt = System.currentTimeMillis();
                    return json(toJson());
                }
                if ("/api/updateClient".equals(uri)) {
                    state.clientLat = dbl(p, "lat", state.clientLat);
                    state.clientLon = dbl(p, "lon", state.clientLon);
                    state.clientName = val(p, "clientName", state.clientName);
                    return json(toJson());
                }
                if ("/api/driverOnline".equals(uri)) {
                    state.driverOnline = true;
                    updateDriverFields(p);
                    if (state.status.length() == 0) state.status = "driver_online";
                    return json(toJson());
                }
                if ("/api/acceptRide".equals(uri)) {
                    state.driverOnline = true;
                    updateDriverFields(p);
                    if (state.rideId.length() == 0) state.rideId = UUID.randomUUID().toString();
                    state.status = "accepted";
                    return json(toJson());
                }
                if ("/api/updateDriver".equals(uri)) {
                    state.driverOnline = true;
                    updateDriverFields(p);
                    if ("accepted".equals(state.status)) state.status = "on_way";
                    return json(toJson());
                }
                if ("/api/arrived".equals(uri)) { state.status = "arrived"; return json(toJson()); }
                if ("/api/start".equals(uri)) { state.status = "started"; return json(toJson()); }
                if ("/api/finish".equals(uri)) { state.status = "finished"; return json(toJson()); }
                if ("/api/cancel".equals(uri)) { state.status = "cancelled"; return json(toJson()); }
                if ("/api/reset".equals(uri)) { state.reset(); return json(toJson()); }
            }
            return cors(newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"error\":\"not_found\"}"));
        } catch (Exception e) {
            return cors(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\":\"" + esc(e.getMessage()) + "\"}"));
        }
    }

    private void updateDriverFields(Map<String, List<String>> p) {
        state.driverLat = dbl(p, "lat", state.driverLat);
        state.driverLon = dbl(p, "lon", state.driverLon);
        state.driverName = val(p, "driverName", state.driverName.length() == 0 ? "Motorista demo" : state.driverName);
        state.carModel = val(p, "carModel", state.carModel.length() == 0 ? "Sedan executivo" : state.carModel);
        state.carColor = val(p, "carColor", state.carColor.length() == 0 ? "Preto" : state.carColor);
        state.carPlate = val(p, "carPlate", state.carPlate.length() == 0 ? "VP-0001" : state.carPlate);
    }

    private Response json(String body) { return cors(newFixedLengthResponse(Response.Status.OK, "application/json", body)); }
    private Response cors(Response r) {
        r.addHeader("Access-Control-Allow-Origin", "*");
        r.addHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        r.addHeader("Access-Control-Allow-Headers", "Content-Type");
        return r;
    }

    private String toJson() {
        synchronized (lock) {
            return "{" +
                "\"rideId\":\"" + esc(state.rideId) + "\"," +
                "\"status\":\"" + esc(state.status) + "\"," +
                "\"clientName\":\"" + esc(state.clientName) + "\"," +
                "\"clientLat\":" + state.clientLat + "," +
                "\"clientLon\":" + state.clientLon + "," +
                "\"destination\":\"" + esc(state.destination) + "\"," +
                "\"note\":\"" + esc(state.note) + "\"," +
                "\"driverOnline\":" + state.driverOnline + "," +
                "\"driverName\":\"" + esc(state.driverName) + "\"," +
                "\"driverLat\":" + state.driverLat + "," +
                "\"driverLon\":" + state.driverLon + "," +
                "\"carModel\":\"" + esc(state.carModel) + "\"," +
                "\"carColor\":\"" + esc(state.carColor) + "\"," +
                "\"carPlate\":\"" + esc(state.carPlate) + "\"," +
                "\"requestedAt\":" + state.requestedAt +
                "}";
        }
    }

    private static String val(Map<String, List<String>> p, String key, String def) {
        List<String> v = p.get(key);
        if (v == null || v.isEmpty() || v.get(0) == null || v.get(0).trim().isEmpty()) return def;
        return v.get(0).trim();
    }
    private static double dbl(Map<String, List<String>> p, String key, double def) {
        try { return Double.parseDouble(val(p, key, String.valueOf(def))); } catch (Exception e) { return def; }
    }
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    private static class RideState {
        String rideId = "";
        String status = "";
        String clientName = "Cliente demo";
        double clientLat = 0;
        double clientLon = 0;
        String destination = "";
        String note = "";
        boolean driverOnline = false;
        String driverName = "";
        double driverLat = 0;
        double driverLon = 0;
        String carModel = "";
        String carColor = "";
        String carPlate = "";
        long requestedAt = 0;
        void reset() {
            rideId = ""; status = ""; clientName = "Cliente demo"; clientLat = 0; clientLon = 0; destination = ""; note = "";
            driverOnline = false; driverName = ""; driverLat = 0; driverLon = 0; carModel = ""; carColor = ""; carPlate = ""; requestedAt = 0;
        }
    }
}
