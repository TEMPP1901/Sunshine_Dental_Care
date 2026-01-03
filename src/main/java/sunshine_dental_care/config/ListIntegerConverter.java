package sunshine_dental_care.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converter để parse comma-separated string thành List<Integer>
 * Ví dụ: "1,2,3" -> [1, 2, 3]
 * Hoặc: "1" -> [1]
 */
@Component
public class ListIntegerConverter implements Converter<String, List<Integer>> {
    
    @Override
    public List<Integer> convert(String source) {
        if (source == null || source.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(source.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }
}

