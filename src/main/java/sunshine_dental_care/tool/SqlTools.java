//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Description;
//import org.springframework.jdbc.core.JdbcTemplate;
//import java.util.List;
//import java.util.Map;
//import java.util.function.Function;
//
//@Configuration
//public class SqlTools {
//
//    @Bean
//    @Description("Execute SQL queries (SELECT, UPDATE, INSERT, DELETE).")
//    public Function<Request, List<Map<String, Object>>> executeSqlQuery(JdbcTemplate jdbcTemplate) {
//        return request -> {
//            String sql = request.query().trim();
//            System.out.println("AI Executing SQL: " + sql);
//            try {
//                // Kiểm tra loại câu lệnh
//                if (sql.toUpperCase().startsWith("SELECT")) {
//                    // Nếu là SELECT: Trả về danh sách dữ liệu
//                    return jdbcTemplate.queryForList(sql);
//                } else {
//                    // Nếu là UPDATE / INSERT / DELETE: Thực thi và trả về số dòng bị ảnh hưởng
//                    int rowsAffected = jdbcTemplate.update(sql);
//                    // Giả lập kết quả trả về dạng List<Map> để AI đọc được
//                    return List.of(Map.of(
//                            "STATUS", "SUCCESS",
//                            "MESSAGE", "Đã thực thi thành công.",
//                            "ROWS_AFFECTED", rowsAffected
//                    ));
//                }
//            } catch (Exception e) {
//                System.err.println("SQL Error: " + e.getMessage());
//                // Trả về lỗi thay vì ném Exception sập nguồn
//                return List.of(Map.of("ERROR", e.getMessage()));
//            }
//        };
//    }
