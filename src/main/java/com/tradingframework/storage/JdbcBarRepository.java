package com.tradingframework.storage;

import com.tradingframework.model.StockBar;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * JDBC repository for {@link StockBar}s in table {@code stock_bar}. Defaults to an embedded H2
 * file database; the SQL is standard apart from the upsert, which uses H2's
 * {@code MERGE INTO ... KEY}. For another database, override {@link #upsertSql()}.
 */
public class JdbcBarRepository implements BarRepository {

    private final String url;
    private final String user;
    private final String password;

    public JdbcBarRepository(String url) {
        this(url, "sa", "");
    }

    public JdbcBarRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        createSchema();
    }

    protected Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    protected String upsertSql() {
        return "MERGE INTO stock_bar (symbol, trade_date, open_price, high_price, low_price, close_price, volume, adj_close) "
                + "KEY (symbol, trade_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    }

    private void createSchema() {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS stock_bar (
                        symbol      VARCHAR(16)      NOT NULL,
                        trade_date  DATE             NOT NULL,
                        open_price  DOUBLE PRECISION NOT NULL,
                        high_price  DOUBLE PRECISION NOT NULL,
                        low_price   DOUBLE PRECISION NOT NULL,
                        close_price DOUBLE PRECISION NOT NULL,
                        volume      BIGINT           NOT NULL,
                        adj_close   DOUBLE PRECISION NOT NULL,
                        PRIMARY KEY (symbol, trade_date)
                    )""");
        } catch (SQLException e) {
            throw new StorageException("Could not create schema", e);
        }
    }

    @Override
    public void saveAll(Collection<StockBar> bars) {
        if (bars.isEmpty()) return;
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(upsertSql())) {
            c.setAutoCommit(false);
            for (StockBar b : bars) {
                ps.setString(1, b.getSymbol().toUpperCase(Locale.ROOT));
                ps.setDate(2, Date.valueOf(b.getDate()));
                ps.setDouble(3, b.getOpen());
                ps.setDouble(4, b.getHigh());
                ps.setDouble(5, b.getLow());
                ps.setDouble(6, b.getClose());
                ps.setDouble(7, b.getVolume());
                ps.setDouble(8, b.getAdjClose());
                ps.addBatch();
            }
            ps.executeBatch();
            c.commit();
        } catch (SQLException e) {
            throw new StorageException("Could not save bars", e);
        }
    }

    @Override
    public List<StockBar> find(String symbol, LocalDate from, LocalDate to) {
        String sql = "SELECT symbol, trade_date, open_price, high_price, low_price, close_price, volume, adj_close "
                + "FROM stock_bar WHERE symbol = ? AND trade_date >= ? AND trade_date <= ? ORDER BY trade_date";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, symbol.toUpperCase(Locale.ROOT));
            ps.setDate(2, Date.valueOf(from == null ? LocalDate.of(1900, 1, 1) : from));
            ps.setDate(3, Date.valueOf(to == null ? LocalDate.of(9999, 12, 31) : to));
            List<StockBar> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new StockBar(rs.getString(1), rs.getDate(2).toLocalDate(),
                            rs.getDouble(3), rs.getDouble(4), rs.getDouble(5), rs.getDouble(6),
                            rs.getLong(7), rs.getDouble(8)));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new StorageException("Could not query bars for " + symbol, e);
        }
    }

    @Override
    public Optional<LocalDate> lastDate(String symbol) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT MAX(trade_date) FROM stock_bar WHERE symbol = ?")) {
            ps.setString(1, symbol.toUpperCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                Date d = rs.next() ? rs.getDate(1) : null;
                return Optional.ofNullable(d).map(Date::toLocalDate);
            }
        } catch (SQLException e) {
            throw new StorageException("Could not query last date for " + symbol, e);
        }
    }

    @Override
    public Set<String> symbols() {
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT symbol FROM stock_bar")) {
            Set<String> out = new TreeSet<>();
            while (rs.next()) out.add(rs.getString(1));
            return out;
        } catch (SQLException e) {
            throw new StorageException("Could not list symbols", e);
        }
    }
}
