package redix.model;

import java.util.List;

public record ProblemResponse(
        String type,

        String title,

        Integer status,

        String code,

        String traceId,

        Existing existing,

        String columnKey,

        List<String> missing,

        List<RowsItem> rows) {

    public record Existing(
            String id,

            String name,

            String vatNumber,

            String mobile,

            String defaultItemId,

            String defaultItemName,

            String status,

            Boolean isActive,

            Integer timesUsed) {
    }

    public record RowsItem(
            String category,

            String item,

            String table,

            Integer row,

            String owner,

            String code,

            String columnKey,

            String value,

            String column) {
    }
}
