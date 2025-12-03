package sunshine_dental_care.utils.huybro_utils.format;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FormatTypeProduct {
    // Tách chuỗi String tyeName theo dạng lấy chữ đầu và các chữ cuối _ đến hết, giữ format in hoa của chuỗi
    public static String autoTypeCode(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return "NA";
        }

        String[] parts = typeName.split("_");
        StringBuilder sb = new StringBuilder();

        for (String p : parts) {
            if (!p.isBlank()) {
                sb.append(p.charAt(0));
            }
        }

        return sb.toString();
    }
    // typeName = từng phần tử String trong allowedTypeNames, format đầu ra của tự động tạo typeName bằng Ai
    public static List<String> resolveTypeNames(
            String productName,
            String productDescription,
            List<String> allowedTypeNames,
            int maxTypes
    ) {
        if (allowedTypeNames == null || allowedTypeNames.isEmpty()) {
            throw new IllegalArgumentException("allowedTypeNames must not be empty");
        }

        if (maxTypes <= 0) {
            maxTypes = 1;
        }

        String text = ((productName == null ? "" : productName) + " " +
                (productDescription == null ? "" : productDescription))
                .toLowerCase();

        Map<String, Integer> scoreMap = new HashMap<>();

        for (String typeName : allowedTypeNames) {
            if (typeName == null || typeName.isBlank()) {
                continue;
            }

            String normalizedType = typeName.toLowerCase().replace("_", " ").trim();
            int score = 0;

            if (!normalizedType.isEmpty() && text.contains(normalizedType)) {
                score += 5;
            }

            String[] tokens = normalizedType.split("\\s+");
            for (String token : tokens) {
                if (token.length() >= 3 && text.contains(token)) {
                    score += 2;
                }
            }

            scoreMap.put(typeName, score);
        }

        List<String> sorted = scoreMap.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            return List.of(allowedTypeNames.get(0));
        }

        List<String> result = sorted.stream()
                .limit(maxTypes)
                .collect(Collectors.toList());

        if (result.isEmpty()) {
            result = List.of(allowedTypeNames.get(0));
        }

        return result;
    }
}
