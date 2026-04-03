package ai.personal.secretary.service;

import ai.personal.secretary.model.ActivityLog;
import ai.personal.secretary.model.Domain;
import ai.personal.secretary.model.UserProfile;
import ai.personal.secretary.repository.ActivityLogRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Интеграция со Strava API.
 *
 * Каждый час проверяет новые активности типа Run и Ride.
 * Автоматически сохраняет их в activity_logs домена "sport".
 *
 * Токен обновляется автоматически через refresh_token.
 * access_token живёт 6 часов — обновляем при каждом запросе если истёк.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StravaService {

    private final ActivityLogRepository activityLogRepository;
    private final DomainRepository domainRepository;
    private final UserProfileRepository userProfileRepository;
    private final ObjectMapper objectMapper;

    @Value("${strava.client-id:}")
    private String clientId;

    @Value("${strava.client-secret:}")
    private String clientSecret;

    @Value("${strava.refresh-token:}")
    private String refreshToken;

    // Кэшируем access token в памяти
    private String accessToken;
    private long tokenExpiresAt = 0;

    private static final Long USER_ID = 2L;
    private static final String STRAVA_API = "https://www.strava.com/api/v3";

    // Когда последний раз синхронизировали (Unix timestamp)
    // При первом запуске берём активности за последние 7 дней
    private long lastSyncTimestamp = Instant.now().minusSeconds(7 * 24 * 3600).getEpochSecond();

    // ── Планировщик: каждый час ───────────────────────────────────────────────

    @Scheduled(cron = "0 0 * * * *")  // каждый час
    public void syncActivities() {
        if (clientId.isBlank() || refreshToken.isBlank()) {
            log.debug("Strava not configured, skipping sync");
            return;
        }

        try {
            String token = getAccessToken();
            if (token == null) return;

            int imported = fetchAndSaveActivities(token);
            if (imported > 0) {
                log.info("Strava sync: imported {} new activities", imported);
            }
        } catch (Exception e) {
            log.error("Strava sync failed: {}", e.getMessage());
        }
    }

    /** Ручной запуск синхронизации — для команды /strava в боте */
    public int syncNow() {
        try {
            String token = getAccessToken();
            if (token == null) return 0;
            return fetchAndSaveActivities(token);
        } catch (Exception e) {
            log.error("Strava manual sync failed: {}", e.getMessage());
            return -1;
        }
    }

    // ── Получение и обновление токена ─────────────────────────────────────────

    private String getAccessToken() throws Exception {
        // Если токен ещё валиден — возвращаем кэшированный
        if (accessToken != null && Instant.now().getEpochSecond() < tokenExpiresAt - 60) {
            return accessToken;
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
        accessToken   = json.get("access_token").asText();
        tokenExpiresAt = json.get("expires_at").asLong();

        // Обновляем refresh_token если Strava вернул новый
        if (json.has("refresh_token")) {
            refreshToken = json.get("refresh_token").asText();
        }

        log.info("Strava token refreshed, expires at {}",
                LocalDateTime.ofInstant(Instant.ofEpochSecond(tokenExpiresAt), ZoneId.systemDefault()));
        return accessToken;
    }

    // ── Загрузка и сохранение активностей ────────────────────────────────────

    @Transactional
    private int fetchAndSaveActivities(String token) throws Exception {
        String url = STRAVA_API + "/athlete/activities?after=" + lastSyncTimestamp + "&per_page=30";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Strava activities fetch failed: {}", response.statusCode());
            return 0;
        }

        JsonNode activities = objectMapper.readTree(response.body());
        if (!activities.isArray() || activities.isEmpty()) return 0;

        // Находим домен sport и пользователя
        Optional<Domain> sportDomain = domainRepository.findByUserIdAndSlug(USER_ID, "sport");
        Optional<UserProfile> user = userProfileRepository.findById(USER_ID);

        if (sportDomain.isEmpty() || user.isEmpty()) {
            log.warn("Sport domain or user not found, skipping Strava import");
            return 0;
        }

        int count = 0;
        long newestTimestamp = lastSyncTimestamp;

        for (JsonNode activity : activities) {
            String type = activity.get("type").asText();

            // Синхронизируем только бег и велосипед
            if (!"Run".equals(type) && !"Ride".equals(type)) continue;

            String name        = activity.get("name").asText();
            double distanceM   = activity.get("distance").asDouble();
            int movingTimeSec  = activity.get("moving_time").asInt();
            long startEpoch    = Instant.parse(activity.get("start_date").asText()).getEpochSecond();
            LocalDateTime startTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(startEpoch), ZoneId.systemDefault());

            // Форматируем читаемое описание
            String typeRu  = "Run".equals(type) ? "🏃 Бег" : "🚴 Велосипед";
            double distKm  = distanceM / 1000.0;
            int minutes    = movingTimeSec / 60;
            String summary = String.format("%s: %s — %.1f км за %d мин",
                    typeRu, name, distKm, minutes);

            // Детали если есть пульс и темп
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

            // Сохраняем в activity_logs
            activityLogRepository.save(ActivityLog.builder()
                    .user(user.get())
                    .domain(sportDomain.get())
                    .loggedAt(startTime)
                    .summary(summary)
                    .details(details.toString())
                    .build());

            if (startEpoch > newestTimestamp) {
                newestTimestamp = startEpoch;
            }
            count++;

            log.debug("Imported Strava activity: {}", summary);
        }

        // Обновляем метку времени последней синхронизации
        if (count > 0) {
            lastSyncTimestamp = newestTimestamp + 1;
        }

        return count;
    }
}