package dev.jax.skylyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

final class TextWrap {
    private TextWrap() {}

    static List<String> wrap(String text, int maxWidth, ToIntFunction<String> width) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && width.applyAsInt(candidate) > maxWidth) {
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            } else {
                cur.setLength(0);
                cur.append(candidate);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        if (out.isEmpty()) {
            out.add(text);
        }
        return out;
    }
}
