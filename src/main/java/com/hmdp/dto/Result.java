package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Boolean success;
    private String errorMsg;
    private Object data;
    private Long total;
    private Integer code;

    // 静态方法封装
    public static Result ok() {
        return new Result(true, null, null, null, 1);
    }

    public static Result ok(Object data) {
        return new Result(true, null, data, null, 1);
    }

    public static Result ok(List<?> data, Long total) {
        return new Result(true, null, data, total, 1);
    }

    public static Result error(String errorMsg) {
        return new Result(false, errorMsg, null, null, 0);
    }

    public static Result error(String errorMsg, Integer code) {
        return new Result(false, errorMsg, null, null, code);
    }
}
