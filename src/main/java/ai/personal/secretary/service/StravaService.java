package ai.personal.secretary.service;

import ai.personal.secretary.model.ActivityLog;
import ai.personal.secretary.model.AppSetting;
import ai.personal.secretary.model.Domain;
import ai.personal.secretary.model.UserProfile;
import ai.personal.secretary.repository.ActivityLogRepository;
import ai.personal.secretary.repository.AppSettingRepository;
import ai.personal.secretary.repository.DomainRepository;
import ai.personal.secretary.repository.UserProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Интеграция со Strava API.
 *
 * Каждый час проверяет новые активности типа Run и Ride.
 * Сохраняет refresh_token в БД — переживает перезапуски.
 * Уведомляет коуча о новых тренировках через callback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StravaService {

    private final ActivityLogRepository activityLogRepository;
    private final AppSettingRepository appSettingRepository;
    private final DomainRepository domainRepository;
    private final UserProfileRepository userProfileRepository;
    private final ObjectMapper objectMapper;

    @Value("${strava.client-id:}")
    private String clientId;

    @Value("${strava.client-secret:}")
    private String clientSecret;

    @Value("${strava.refresh-token:}")
    private String refreshTokenFromConfig;

    // Кэш в памяти
    private String accessToken;
    private long tokenExpiresAt = 0;

    private static final Long USER_ID = 2L;
    private static final String STRAVA_API = "https://www.strava.com/api/v3";
    private static final String KEY_REFRESH_TOKEN  = "strava.refresh_token";
    private static final String KEY_LAST_SYNC      = "strava.last_sync_timestamp";

    // Callback для уведомления бота о новых тренировках
    private java.util.function.Consumer<List<ActivityLog>> onNewActivities;

    public void setOnNewActivities(java.util.function.Consumer<List<ActivityLog>> callback) {
        this.onNewActivities = callback;
    }

    // ── Планировщик: каждый час ───────────────────────────────────────────────

    @Scheduled(cron = "0 0 * * * *")
    public void syncActivities() {
        if (clientId.isBlank()) {
            log.debug("Strava not configured, skipping sync");
            return;
        }
        try {
            String token = getAccessToken();
            if (token == null) return;

            List<ActivityLog> imported = fetchAndSaveActivities(token);
            if (!imported.isEmpty()) {
                log.info("Strava sync: imported {} new activities", imported.size());
                // Уведомляем бота
                if (onNewActivities != null) {
                    onNewActivities.accept(imported);
                }
            }
        } catch (Exception e) {
            log.error("Strava sync failed: {}", e.getMessage());
        }
    }

    /** Ручной запуск — для команды /strava */
    public List<ActivityLog> syncNow() {
        try {
            String token = getAccessToken();
            if (token == null) return List.of();
            return fetchAndSaveActivities(token);
        } catch (Exception e) {
            log.error("Strava manual sync failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Токен ─────────────────────────────────────────────────────────────────

    private String getAccessToken() throws Exception {
        if (accessToken != null && Instant.now().getEpochSecond() < tokenExpiresAt - 60) {
            return accessToken;
        }

        String refreshToken = loadRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("Strava refresh token not found");
            return null;
        }

        log.debug("Refreshing Strava access token...");

        String body = "client_id=" + clientId +
                "&client_secret=" + clientSecret +
                "&refresh_token=" + refreshToken +
                "&grant_type=refresh_token";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.strava.com/oauth/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Failed to refresh Strava token: {} {}", response.statusCode(), response.body());
            return null;
        }

        JsonNode json = objectMapper.readTree(response.body());
        accessToken    = json.get("access_token").asText();
        tokenExpiresAt = json.get("expires_at").asLong();

        // Сохраняем новый refresh_token в БД
        if (json.has("refresh_token")) {
            saveRefreshToken(json.get("refresh_token").asText());
        }

        log.info("Strava token refreshed, expires at {}",
                LocalDateTime.ofInstant(Instant.ofEpochSecond(tokenExpiresAt), ZoneId.systemDefault()));
        return accessToken;
    }

    private String loadRefreshToken() {
        // Сначала ищем в БД
        Optional<AppSetting> stored = appSettingRepository.findByKey(KEY_REFRESH_TOKEN);
        if (stored.isPresent() && !stored.get().getValue().isBlank()) {
            return stored.get().getValue();
        }
        // Fallback — из конфига (start.sh)
        if (!refreshTokenFromConfig.isBlank()) {
            // Сохраняем в БД чтобы в следующий раз брать оттуда
            saveRefreshToken(refreshTokenFromConfig);
            return refreshTokenFromConfig;
        }
        return null;
    }

    private void saveRefreshToken(String token) {
        appSettingRepository.save(AppSetting.builder()
                .key(KEY_REFRESH_TOKEN)
                .value(token)
                .build());
        log.debug("Strava refresh_token saved to DB");
    }

    // ── Загрузка активностей ──────────────────────────────────────────────────

    @Transactional
    protected List<ActivityLog> fetchAndSaveActivities(String token) throws Exception {
        long lastSync = loadLastSyncTimestamp();
        String url = STRAVA_API + "/athlete/activities?after=" + lastSync + "&per_page=30";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Strava activities fetch failed: {}", response.statusCode());
            return List.of();
        }

        JsonNode activities = objectMapper.readTree(response.body());
        if (!activities.isArray() || activities.isEmpty()) return List.of();

        Optional<Domain> sportDomain = domainRepository.findByUserIdAndSlug(USER_ID, "sport");
        Optional<UserProfile> user   = userProfileRepository.findById(USER_ID);
        if (sportDomain.isEmpty() || user.isEmpty()) return List.of();

        List<ActivityLog> imported = new ArrayList<>();
        long newestTimestamp = lastSync;

        for (JsonNode activity : activities) {
            String type = activity.get("type").asText();
            if (!"Run".equals(type) && !"Ride".equals(type)) continue;

            String name       = activity.get("name").asText();
            double distanceM  = activity.get("distance").asDouble();
            int movingTimeSec = activity.get("moving_time").asInt();
            long startEpoch   = Instant.parse(activity.get("start_date").asText()).getEpochSecond();
            LocalDateTime startTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(startEpoch), ZoneId.systemDefault());

            String typeRu  = "Run".equals(type) ? "🏃 Бег" : "🚴 Велосипед";
            double distKm  = distanceM / 1000.0;
            int minutes    = movingTimeSec / 60;
            String summary = String.format("%s: %s — %.1f км за %d мин",
                    typeRu, name, distKm, minutes);

            StringBuilder details = new StringBuilder();
            details.append(String.format("Дистанция: %.2f км\n", distKm));
            details.append(String.format("Время: %d мин %d сек\n", minutes, movingTimeSec % 60));

            if (distanceM > 0 && movingTimeSec > 0) {
                double paceSecPerKm = movingTimeSec / (distanceM / 1000.0);
                int paceMin = (int) paceSecPerKm / 60;
                int paceSec = (int) paceSecPerKm % 60;
                details.append(String.format("Темп: %d:%02d /км\n", paceMin, paceSec));
            }
            if (activity.has("average_heartrate") && !activity.get("average_heartrate").isNull()) {
                details.append(String.format("Пульс ср: %d bpm\n",
                        activity.get("average_heartrate").asInt()));
            }
            if (activity.has("total_elevation_gain")) {
                details.append(String.format("Набор высоты: %d м\n",
                        activity.get("total_elevation_gain").asInt()));
            }

            ActivityLog saved = activityLogRepository.save(ActivityLog.builder()
                    .user(user.get())
                    .domain(sportDomain.get())
                    .loggedAt(startTime)
                    .summary(summary)
                    .details(details.toString())
                    .build());

            imported.add(saved);
            if (startEpoch > newestTimestamp) newestTimestamp = startEpoch;
            log.debug("Imported: {}", summary);
        }

        if (!imported.isEmpty()) {
            saveLastSyncTimestamp(newestTimestamp + 1);
        }

        return imported;
    }

    private long loadLastSyncTimestamp() {
        return appSettingRepository.findByKey(KEY_LAST_SYNC)
                .map(s -> Long.parseLong(s.getValue()))
                .orElse(Instant.now().minusSeconds(7 * 24 * 3600).getEpochSecond());
    }

    private void saveLastSyncTimestamp(long timestamp) {
        appSettingRepository.save(AppSetting.builder()
                .key(KEY_LAST_SYNC)
                .value(String.valueOf(timestamp))
                .build());
    }
}