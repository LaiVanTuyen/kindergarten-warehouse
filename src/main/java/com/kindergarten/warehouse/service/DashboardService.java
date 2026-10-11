package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Totals are current inventory; dated charts count creation events only.
 * View counters have no historical event series, so we never invent one. */
@Service
@RequiredArgsConstructor
public class DashboardService {
    private final JdbcTemplate jdbc;
    private final ResourceStatService counters;
    record Range(LocalDate start, LocalDate end) {}

    Range range(String period, String start, String end) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        try {
            Range result = switch (period) {
                case "last_7_days" -> new Range(today.minusDays(6), today);
                case "this_month" -> new Range(today.withDayOfMonth(1), today);
                case "this_year" -> new Range(today.withDayOfYear(1), today);
                case "custom" -> new Range(LocalDate.parse(start), LocalDate.parse(end));
                default -> throw new IllegalArgumentException();
            };
            if (result.end.isBefore(result.start) || result.end.toEpochDay() - result.start.toEpochDay() > 3660)
                throw new IllegalArgumentException();
            return result;
        } catch (RuntimeException ex) { throw new AppException(ErrorCode.INVALID_REQUEST); }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview(String period, String startDate, String endDate) {
        Range range = range(period, startDate, endDate);
        Object start = range.start.atStartOfDay(), end = range.end.plusDays(1).atStartOfDay();
        List<Map<String,Object>> resources = jdbc.queryForList("""
            SELECT r.id, r.title, r.thumbnail_url AS thumbnailUrl, COALESCE(r.views_count,0) AS views,
              r.created_at AS createdAt, t.name AS topic
            FROM resources r JOIN topics t ON r.topic_id=t.id JOIN categories c ON t.category_id=c.id
            WHERE r.is_deleted=0 AND r.status<>'DRAFT' AND t.is_deleted=0 AND c.is_deleted=0
            """);
        List<String> ids = resources.stream().map(r -> (String) r.get("id")).toList();
        Map<String,Long> pendingViews = counters.getPendingViewCounts(ids);
        long views = 0;
        for (Map<String,Object> resource : resources) {
            long count = ((Number)resource.getOrDefault("views", 0L)).longValue()
                    + pendingViews.getOrDefault((String)resource.get("id"), 0L);
            resource.put("views", count); views += count;
            resource.remove("createdAt");
        }
        long users = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE is_deleted=0", Long.class);
        long pending = jdbc.queryForObject("""
            SELECT COUNT(*) FROM resources r JOIN topics t ON r.topic_id=t.id JOIN categories c ON t.category_id=c.id
            WHERE r.is_deleted=0 AND r.status='PENDING' AND t.is_deleted=0 AND c.is_deleted=0
            """, Long.class);
        Map<String,Object> trendPct = new LinkedHashMap<>();
        trendPct.put("resources", null); trendPct.put("views", null); trendPct.put("users", null);
        Map<String,Object> stats = Map.of("totalResources",resources.size(),"totalViews",views,
            "totalUsers",users,"pendingApprovals",pending,"trendPct",trendPct);
        Map<String,Long> resourceDays = countsByDay("resources", start, end);
        Map<String,Long> userDays = countsByDay("users", start, end);
        List<String> labels = new ArrayList<>(); List<Long> resourceSeries=new ArrayList<>(), userSeries=new ArrayList<>();
        // Monthly buckets for long ranges; otherwise daily. No fabricated view history.
        boolean monthly = range.end.toEpochDay()-range.start.toEpochDay() > 62;
        LocalDate cursor = range.start;
        while (!cursor.isAfter(range.end)) {
            LocalDate next = monthly ? cursor.withDayOfMonth(1).plusMonths(1) : cursor.plusDays(1);
            LocalDate bucketEnd = next.isAfter(range.end.plusDays(1)) ? range.end.plusDays(1) : next;
            long r=0,u=0;
            for(LocalDate day=cursor;day.isBefore(bucketEnd);day=day.plusDays(1)) {
                r+=resourceDays.getOrDefault(day.toString(),0L);u+=userDays.getOrDefault(day.toString(),0L);
            }
            labels.add(cursor.format(DateTimeFormatter.ofPattern(monthly?"MM/yyyy":"dd/MM")));
            resourceSeries.add(r);userSeries.add(u);cursor=next;
        }
        List<Map<String,Object>> queue = jdbc.queryForList("""
            SELECT r.id,r.title,u.full_name AS uploader,u.avatar_url AS uploaderAvatarUrl,
              r.thumbnail_url AS thumbnailUrl,t.name AS topic,r.created_at AS submittedAt
            FROM resources r JOIN users u ON r.created_by=u.id JOIN topics t ON r.topic_id=t.id
              JOIN categories c ON t.category_id=c.id
            WHERE r.is_deleted=0 AND r.status='PENDING' AND t.is_deleted=0 AND c.is_deleted=0
            ORDER BY r.created_at ASC,r.id ASC LIMIT 5
            """);
        timestampStrings(queue, "submittedAt");
        resources.sort(Comparator.<Map<String,Object>>comparingLong(r -> ((Number)r.get("views")).longValue()).reversed()
                .thenComparing(r -> (String)r.get("id")));
        List<Map<String,Object>> teachers = jdbc.queryForList("""
            SELECT u.id,u.full_name AS fullName,u.avatar_url AS avatarUrl,COUNT(r.id) AS uploadCount
            FROM users u JOIN resources r ON r.created_by=u.id JOIN topics t ON r.topic_id=t.id
              JOIN categories c ON t.category_id=c.id
            WHERE u.is_deleted=0 AND r.is_deleted=0 AND r.status<>'DRAFT' AND t.is_deleted=0 AND c.is_deleted=0
              AND EXISTS(SELECT 1 FROM user_roles ur WHERE ur.user_id=u.id AND ur.role='TEACHER')
              AND r.created_at>=? AND r.created_at<?
            GROUP BY u.id,u.full_name,u.avatar_url ORDER BY uploadCount DESC,u.id ASC LIMIT 5
            """, start, end);
        List<Map<String,Object>> topics = jdbc.queryForList("""
            SELECT t.name AS topic,COUNT(r.id) AS count FROM resources r JOIN topics t ON r.topic_id=t.id
              JOIN categories c ON t.category_id=c.id
            WHERE r.is_deleted=0 AND r.status<>'DRAFT' AND t.is_deleted=0 AND c.is_deleted=0
              AND r.created_at>=? AND r.created_at<?
            GROUP BY t.id,t.name ORDER BY count DESC,t.id ASC
            """, start, end);
        List<Map<String,Object>> activity = jdbc.queryForList("""
            SELECT id,username AS actor,action,target,timestamp FROM audit_log ORDER BY timestamp DESC,id DESC LIMIT 8
            """);
        timestampStrings(activity, "timestamp");
        for (Map<String,Object> item : activity) {
            String action = item.get("action").toString().toUpperCase(Locale.ROOT);
            // Retain the original action as text; unknown actions have a neutral display.
            item.put("target", action + " · " + Objects.toString(item.get("target"), ""));
            item.put("action", action.contains("APPROVE")?"APPROVE":action.contains("REJECT")?"REJECT":
                action.contains("DELETE")?"DELETE":action.contains("CREATE")?"UPLOAD":action.contains("LOGIN")?"LOGIN":"UPDATE");
        }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("stats",stats);result.put("trend",Map.of("xAxis",labels,"resources",resourceSeries,"users",userSeries,"views",List.of()));
        result.put("pending",queue);result.put("topResources",resources.stream().limit(5).toList());
        result.put("topTeachers",teachers);result.put("topicDistribution",topics);result.put("activity",activity);
        return result;
    }

    private Map<String,Long> countsByDay(String table,Object start,Object end) {
        // table is an internal constant, never user-provided.
        String sql = table.equals("resources") ? """
            SELECT DATE(r.created_at) AS day,COUNT(*) AS count FROM resources r
              JOIN topics t ON r.topic_id=t.id JOIN categories c ON t.category_id=c.id
            WHERE r.is_deleted=0 AND r.status<>'DRAFT' AND t.is_deleted=0 AND c.is_deleted=0
              AND r.created_at>=? AND r.created_at<? GROUP BY DATE(r.created_at)
            """ : "SELECT DATE(created_at) AS day,COUNT(*) AS count FROM users WHERE is_deleted=0 AND created_at>=? AND created_at<? GROUP BY DATE(created_at)";
        List<Map<String,Object>> rows=jdbc.queryForList(sql,start,end);
        Map<String,Long> result=new HashMap<>();
        rows.forEach(r -> result.put(r.get("day").toString(),((Number)r.get("count")).longValue()));return result;
    }
    private void timestampStrings(List<Map<String,Object>> rows,String field) {
        for(Map<String,Object> row:rows) {
            Object value=row.get(field);
            if(value instanceof java.sql.Timestamp timestamp)row.put(field,timestamp.toLocalDateTime().atOffset(ZoneOffset.UTC).toString());
        }
    }
}
