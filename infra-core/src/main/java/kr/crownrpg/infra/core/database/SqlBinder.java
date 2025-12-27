package kr.crownrpg.infra.core.database;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;

public final class SqlBinder {

    private SqlBinder() {}

    public static void bind(PreparedStatement ps, Object... params) throws Exception {
        if (params == null || params.length == 0) return;

        for (int i = 0; i < params.length; i++) {
            Object p = params[i];

            // Instant -> Timestamp 변환 편의
            if (p instanceof Instant ins) {
                p = Timestamp.from(ins);
            }
            ps.setObject(i + 1, p);
        }
    }
}
