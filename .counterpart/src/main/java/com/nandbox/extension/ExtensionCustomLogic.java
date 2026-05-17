package com.nandbox.extension;

import com.nandbox.bots.api.Nandbox;
import com.nandbox.bots.api.NandboxClient;
import com.nandbox.bots.api.data.*;
import com.nandbox.bots.api.inmessages.*;
import com.nandbox.bots.api.outmessages.*;
import com.nandbox.bots.api.util.*;
import com.nandbox.bots.api.test.*;

import net.minidev.json.*;
import net.minidev.json.parser.JSONParser;

import com.nandbox.extension.ExtensionAdapter;
import java.io.FileInputStream;
import java.io.IOException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Properties;

public class ExtensionCustomLogic extends ExtensionAdapter {
    private Nandbox.Api api;

    private static final String API_BASE_URL = "http://api.weatherapi.com/v1/";
    private static final String API_KEY = "4e57492e251f4907834122714252304";
    private static final int DEFAULT_FORECAST_DAYS = 3;

    public static void main(String[] args) throws Exception {
        String TOKEN = "";
        Properties properties = new Properties();
        FileInputStream input = null;
        try {
            input = new FileInputStream("config.properties");
            properties.load(input);
            TOKEN = properties.getProperty("Token");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                }
            }
        }

        NandboxClient client = NandboxClient.get();
        client.connect(TOKEN, new ExtensionCustomLogic());
    }

    @Override
    public void onConnect(Nandbox.Api api) {
        this.api = api;
    }

    @Override
    public void onReceive(IncomingMessage incomingMsg) {
        if (incomingMsg == null || incomingMsg.getChat() == null || incomingMsg.getFrom() == null) {
            return;
        }

        String chatId = incomingMsg.getChat().getId();
        String text = incomingMsg.getText();
        String reference = Utils.getUniqueId();
        String userId = incomingMsg.getFrom().getId();
        String appId = incomingMsg.getAppId();
        Integer chatSettings = incomingMsg.getChatSettings();

        if (text == null) {
            return;
        }

        String trimmed = text.trim();
        if (trimmed.length() == 0) {
            return;
        }

        String lower = trimmed.toLowerCase();

        if (lower.equals("/start") || lower.equals("start")) {
            sendTextSafe(chatId,
                    "Your handy weather guide\n\nCommands AAAAAAAAAAAAAAAAAA:\n/weather <city>\n/forecast <city>",
                    reference, userId, chatSettings, appId);
            return;
        }

        if (lower.startsWith("/weather") || lower.startsWith("weather")) {
            String city = extractArgument(trimmed, "weather");
            if (city == null || city.trim().length() == 0) {
                sendTextSafe(chatId,
                        "Please provide a city name.\nExample: /weather London",
                        reference, userId, chatSettings, appId);
                return;
            }

            sendTextSafe(chatId,
                    "Fetching current weather for \"" + city.trim() + "\"...",
                    Utils.getUniqueId(), userId, chatSettings, appId);

            try {
                String report = fetchCurrentWeather(city.trim());
                sendTextSafe(chatId, report, Utils.getUniqueId(), userId, chatSettings, appId);
            } catch (Exception ex) {
                sendTextSafe(chatId, "Couldn't fetch weather right now. Please try again later.", Utils.getUniqueId(), userId,
                        chatSettings, appId);
            }
            return;
        }

        if (lower.startsWith("/forecast") || lower.startsWith("forecast")) {
            String city = extractArgument(trimmed, "forecast");
            if (city == null || city.trim().length() == 0) {
                sendTextSafe(chatId,
                        "Please provide a city name.\nExample: /forecast London",
                        reference, userId, chatSettings, appId);
                return;
            }

            sendTextSafe(chatId,
                    "Fetching forecast for \"" + city.trim() + "\"...",
                    Utils.getUniqueId(), userId, chatSettings, appId);

            try {
                String report = fetchForecast(city.trim(), DEFAULT_FORECAST_DAYS);
                sendTextSafe(chatId, report, Utils.getUniqueId(), userId, chatSettings, appId);
            } catch (Exception ex) {
                sendTextSafe(chatId, "Couldn't fetch forecast right now. Please try again later.", Utils.getUniqueId(), userId,
                        chatSettings, appId);
            }
            return;
        }

        sendTextSafe(chatId,
                "I can fetch weather updates.\n\nCommands:\n/weather <city>\n/forecast <city>",
                reference, userId, chatSettings, appId);
    }

    private void sendTextSafe(String chatId, String msg, String reference, String userId, Integer chatSettings, String appId) {
        if (api == null) {
            return;
        }
        if (msg == null) {
            msg = "";
        }
        api.sendText(
                chatId,
                msg,
                reference,
                null,
                userId,
                new Integer(0),
                Boolean.FALSE,
                chatSettings,
                null,
                null,
                null,
                appId
        );
    }

    private String extractArgument(String input, String commandNoSlash) {
        if (input == null) {
            return null;
        }
        String s = input.trim();
        if (s.length() == 0) {
            return null;
        }

        String lower = s.toLowerCase();
        String cmd1 = "/" + commandNoSlash;
        String cmd2 = commandNoSlash;

        if (lower.startsWith(cmd1)) {
            s = s.substring(cmd1.length()).trim();
        } else if (lower.startsWith(cmd2)) {
            s = s.substring(cmd2.length()).trim();
        }

        if (s.startsWith("@")) {
            int sp = s.indexOf(' ');
            if (sp > 0) {
                s = s.substring(sp + 1).trim();
            }
        }

        return s;
    }

    private String fetchCurrentWeather(String city) throws Exception {
        String q = URLEncoder.encode(city, "UTF-8");
        String urlStr = API_BASE_URL + "current.json?key=" + URLEncoder.encode(API_KEY, "UTF-8") + "&q=" + q + "&aqi=no";
        return performWeatherApiRequest(urlStr, "current", city);
    }

    private String fetchForecast(String city, int days) throws Exception {
        if (days <= 0) {
            days = 1;
        }
        String q = URLEncoder.encode(city, "UTF-8");
        String urlStr = API_BASE_URL + "forecast.json?key=" + URLEncoder.encode(API_KEY, "UTF-8") + "&q=" + q + "&days=" + days + "&aqi=no&alerts=no";
        return performWeatherApiRequest(urlStr, "forecast", city);
    }

    private String performWeatherApiRequest(String urlStr, String mode, String city) throws Exception {
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(25000);
            conn.setDoInput(true);

            int status = conn.getResponseCode();
            if (status >= 200 && status < 300) {
                is = conn.getInputStream();
            } else {
                is = conn.getErrorStream();
                String errBody = readAll(is);
                String extracted = parseWeatherApiError(errBody);
                if (extracted != null) {
                    return extracted;
                }
                throw new IOException("Weather API error HTTP " + status);
            }

            String body = readAll(is);
            if ("forecast".equals(mode)) {
                return formatForecastResponse(body, city);
            }
            return formatCurrentWeatherResponse(body, city);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String parseWeatherApiError(String body) {
        if (body == null || body.trim().length() == 0) {
            return null;
        }
        try {
            JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);
            Object o = p.parse(body);
            if (!(o instanceof JSONObject)) {
                return null;
            }
            JSONObject root = (JSONObject) o;
            Object errObj = root.get("error");
            if (errObj instanceof JSONObject) {
                JSONObject err = (JSONObject) errObj;
                Object msg = err.get("message");
                if (msg != null) {
                    return "Weather service error: " + String.valueOf(msg);
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    private String formatCurrentWeatherResponse(String body, String requestedCity) throws Exception {
        JSONParser parser = new JSONParser(JSONParser.MODE_PERMISSIVE);
        Object parsed = parser.parse(body);
        if (!(parsed instanceof JSONObject)) {
            throw new IOException("Unexpected response");
        }
        JSONObject root = (JSONObject) parsed;

        JSONObject location = (JSONObject) root.get("location");
        JSONObject current = (JSONObject) root.get("current");
        if (location == null || current == null) {
            throw new IOException("Malformed response");
        }

        String placeLine = formatPlaceLine(location, requestedCity);

        Double tempC = safeDouble(current.get("temp_c"));
        Double feelsC = safeDouble(current.get("feelslike_c"));
        Integer humidity = safeInt(current.get("humidity"));
        Double windKph = safeDouble(current.get("wind_kph"));
        String windDir = safeStr(current.get("wind_dir"));
        String localtime = safeStr(location.get("localtime"));

        String conditionText = "";
        Object condObj = current.get("condition");
        if (condObj instanceof JSONObject) {
            conditionText = safeStr(((JSONObject) condObj).get("text"));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Current weather").append(placeLine).append("\n");

        if (conditionText.length() > 0) {
            sb.append("Condition: ").append(conditionText).append("\n");
        }
        if (tempC != null) {
            sb.append("Temperature: ").append(trimDouble(tempC)).append("°C");
            if (feelsC != null) {
                sb.append(" (feels ").append(trimDouble(feelsC)).append("°C)");
            }
            sb.append("\n");
        }
        if (humidity != null) {
            sb.append("Humidity: ").append(humidity.intValue()).append("%\n");
        }
        if (windKph != null) {
            sb.append("Wind: ").append(trimDouble(windKph)).append(" kph");
            if (windDir.length() > 0) {
                sb.append(" ").append(windDir);
            }
            sb.append("\n");
        }
        if (localtime.length() > 0) {
            sb.append("Local time: ").append(localtime);
        }

        return sb.toString().trim();
    }

    private String formatForecastResponse(String body, String requestedCity) throws Exception {
        JSONParser parser = new JSONParser(JSONParser.MODE_PERMISSIVE);
        Object parsed = parser.parse(body);
        if (!(parsed instanceof JSONObject)) {
            throw new IOException("Unexpected response");
        }
        JSONObject root = (JSONObject) parsed;

        JSONObject location = (JSONObject) root.get("location");
        JSONObject forecast = (JSONObject) root.get("forecast");
        if (location == null || forecast == null) {
            throw new IOException("Malformed response");
        }

        JSONArray daysArr = null;
        Object fd = forecast.get("forecastday");
        if (fd instanceof JSONArray) {
            daysArr = (JSONArray) fd;
        }
        if (daysArr == null || daysArr.size() == 0) {
            throw new IOException("No forecast data");
        }

        String placeLine = formatPlaceLine(location, requestedCity);

        StringBuilder sb = new StringBuilder();
        sb.append("Forecast").append(placeLine).append("\n");

        for (int i = 0; i < daysArr.size(); i++) {
            Object dayObj = daysArr.get(i);
            if (!(dayObj instanceof JSONObject)) {
                continue;
            }
            JSONObject dayRoot = (JSONObject) dayObj;
            String date = safeStr(dayRoot.get("date"));
            JSONObject day = null;
            Object dayVal = dayRoot.get("day");
            if (dayVal instanceof JSONObject) {
                day = (JSONObject) dayVal;
            }
            if (day == null) {
                continue;
            }

            Double maxC = safeDouble(day.get("maxtemp_c"));
            Double minC = safeDouble(day.get("mintemp_c"));
            Double avgC = safeDouble(day.get("avgtemp_c"));
            Integer humidity = safeInt(day.get("avghumidity"));
            Double maxWind = safeDouble(day.get("maxwind_kph"));

            String conditionText = "";
            Object condObj = day.get("condition");
            if (condObj instanceof JSONObject) {
                conditionText = safeStr(((JSONObject) condObj).get("text"));
            }

            if (i > 0) {
                sb.append("\n");
            }
            sb.append(date.length() > 0 ? date : ("Day " + (i + 1))).append(":\n");
            if (conditionText.length() > 0) {
                sb.append("  Condition: ").append(conditionText).append("\n");
            }
            if (minC != null || maxC != null || avgC != null) {
                sb.append("  Temp (°C): ");
                boolean wrote = false;
                if (minC != null) {
                    sb.append("min ").append(trimDouble(minC));
                    wrote = true;
                }
                if (maxC != null) {
                    if (wrote) {
                        sb.append(", ");
                    }
                    sb.append("max ").append(trimDouble(maxC));
                    wrote = true;
                }
                if (avgC != null) {
                    if (wrote) {
                        sb.append(", ");
                    }
                    sb.append("avg ").append(trimDouble(avgC));
                }
                sb.append("\n");
            }
            if (humidity != null) {
                sb.append("  Avg humidity: ").append(humidity.intValue()).append("%\n");
            }
            if (maxWind != null) {
                sb.append("  Max wind: ").append(trimDouble(maxWind)).append(" kph\n");
            }
        }

        return sb.toString().trim();
    }

    private String formatPlaceLine(JSONObject location, String requestedCity) {
        if (location == null) {
            if (requestedCity == null || requestedCity.length() == 0) {
                return "";
            }
            return " for " + requestedCity;
        }
        String name = safeStr(location.get("name"));
        String region = safeStr(location.get("region"));
        String country = safeStr(location.get("country"));

        StringBuilder place = new StringBuilder();
        if (name.length() > 0) {
            place.append(" for ").append(name);
            if (region.length() > 0) {
                place.append(", ").append(region);
            }
            if (country.length() > 0) {
                place.append(", ").append(country);
            }
        } else if (requestedCity != null && requestedCity.length() > 0) {
            place.append(" for ").append(requestedCity);
        }
        return place.toString();
    }

    private String readAll(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuffer sb = new StringBuffer();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
            sb.append('\n');
        }
        return sb.toString();
    }

    private String safeStr(Object o) {
        if (o == null) {
            return "";
        }
        return String.valueOf(o);
    }

    private Double safeDouble(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return new Double(((Number) o).doubleValue());
        }
        try {
            return new Double(Double.parseDouble(String.valueOf(o)));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer safeInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return new Integer(((Number) o).intValue());
        }
        try {
            return new Integer(Integer.parseInt(String.valueOf(o)));
        } catch (Exception e) {
            return null;
        }
    }

    private String trimDouble(Double d) {
        if (d == null) {
            return "";
        }
        double v = d.doubleValue();
        long asLong = (long) v;
        if (v == (double) asLong) {
            return String.valueOf(asLong);
        }
        String s = String.valueOf(v);
        if (s.indexOf('.') >= 0) {
            while (s.endsWith("0")) {
                s = s.substring(0, s.length() - 1);
            }
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
    }
}
