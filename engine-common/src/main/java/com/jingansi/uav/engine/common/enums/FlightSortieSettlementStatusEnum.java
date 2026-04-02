package com.jingansi.uav.engine.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 飞行架次结算状态枚举。
 */
@Getter
@AllArgsConstructor
public enum FlightSortieSettlementStatusEnum {

    /**
     * 当前架次还在进行中，只是占位记录，尚未完成结算。
     */
    UNSETTLED(0, "未结算"),

    /**
     * 当前架次已经根据累计值差分完成结算。
     */
    SETTLED(1, "已结算");

    private final Integer code;
    private final String desc;

    public static String descOf(Integer code) {
        for (FlightSortieSettlementStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item.desc;
            }
        }
        return "未知";
    }
}
