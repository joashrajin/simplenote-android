package com.automattic.simplenote.utils;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@SuppressWarnings("TryFinallyCanBeTryWithResources") // Preserve successful reads when cleanup fails.
public class ContextUtils {
    public static String readCssFile(Context context, String css) {
        InputStream stream = null;
        BufferedReader reader = null;
        StringBuilder builder = new StringBuilder();
        String line;

        try {
            stream = context.getResources().getAssets().open(css);
            reader = new BufferedReader(new InputStreamReader(stream));

            while ((line = reader.readLine()) != null) {
                builder.append(line);
                builder.append('\n');
            }

            return builder.toString();
        } catch (IOException ex) {
            return null;
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                } else if (stream != null) {
                    stream.close();
                }
            } catch (IOException ignored) {
            }
        }
    }
}
