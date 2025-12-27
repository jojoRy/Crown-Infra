package kr.crownrpg.infra.api.database;

import java.util.Optional;

public interface Row {

    String getString(String column);

    int getInt(String column);

    long getLong(String column);

    double getDouble(String column);

    boolean getBoolean(String column);

    Optional<Object> get(String column);
}
