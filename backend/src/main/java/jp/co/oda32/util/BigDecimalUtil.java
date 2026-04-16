package jp.co.oda32.util;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * BigDecimal用Utilクラス
 *
 * @author k_oda
 * @since 2018/08/10
 */
@Slf4j
public class BigDecimalUtil {
    public static final BigDecimal MAX_INT8_DECIMAL4 = new BigDecimal("99999999.9999");
    public static final BigDecimal MAX_INT8_DECIMAL2 = new BigDecimal("99999999.99");
    public static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("10.00");

    /**
     * 消費税率が NULL の場合、デフォルト 10.00 を返し WARN ログを出力する。
     * SMILE 連携ファイルで税率が空のデータを検知するため。
     */
    public static BigDecimal requireTaxRate(BigDecimal value, String context) {
        if (value == null) {
            log.warn("消費税率が NULL のため 10.00 で補完します。{}", context);
            return DEFAULT_TAX_RATE;
        }
        return value;
    }

    public static BigDecimal convertNullToZero(BigDecimal value) {
        if (value == null) {
            value = BigDecimal.ZERO;
        }
        return value;
    }

    public static boolean isZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0;
    }

    public static boolean isNullZero(BigDecimal value) {
        return convertNullToZero(value).compareTo(BigDecimal.ZERO) == 0;
    }

    public static boolean isNegative(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0;
    }

    public static boolean isPositive(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isEqual(BigDecimal val1, BigDecimal val2) {
        return convertNullToZero(val1).compareTo(convertNullToZero(val2)) == 0;
    }

    public static BigDecimal add(BigDecimal underAdd, BigDecimal givenAdd) {
        underAdd = convertNullToZero(underAdd);
        givenAdd = convertNullToZero(givenAdd);
        return underAdd.add(givenAdd);
    }

    public static BigDecimal subtract(BigDecimal underSubtract, BigDecimal givenSubtract) {
        underSubtract = convertNullToZero(underSubtract);
        givenSubtract = convertNullToZero(givenSubtract);
        return underSubtract.subtract(givenSubtract);
    }

    public static BigDecimal multiply(BigDecimal underMultiply, BigDecimal givenMultiply) {
        underMultiply = convertNullToZero(underMultiply);
        givenMultiply = convertNullToZero(givenMultiply);
        return underMultiply.multiply(givenMultiply);
    }

    public static BigDecimal divide(BigDecimal underDivide, BigDecimal givenDivide) {
        underDivide = convertNullToZero(underDivide);
        givenDivide = convertNullToZero(givenDivide);
        if (givenDivide.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("分母に0が指定されました。");
        }
        return underDivide.divide(givenDivide, MathContext.DECIMAL128);
    }

    public static BigDecimal convertStringToBigDecimal(String value) {
        BigDecimal resultValue;
        if (StringUtil.isEmpty(value)) {
            resultValue = BigDecimal.ZERO;
        } else {
            value = value.replace(",", "");
            try {
                resultValue = new BigDecimal(value);
            } catch (NumberFormatException e) {
                resultValue = BigDecimal.ZERO;
            }
        }
        return resultValue;
    }

    public static BigDecimal roundUp(BigDecimal value, int round) {
        value = convertNullToZero(value);
        return value.setScale(round - 1, RoundingMode.UP);
    }

    public static BigDecimal roundDown(BigDecimal value, int round) {
        value = convertNullToZero(value);
        return value.setScale(round - 1, RoundingMode.DOWN);
    }

    public static BigDecimal roundHalfUp(BigDecimal value, int round) {
        value = convertNullToZero(value);
        return value.setScale(round - 1, RoundingMode.HALF_UP);
    }

    public static BigDecimal limitMultiply(BigDecimal underMultiply, BigDecimal givenMultiply, BigDecimal limit) {
        BigDecimal calc = multiply(underMultiply, givenMultiply);
        if (calc.compareTo(limit) > 0) {
            return limit;
        } else {
            return calc;
        }
    }

    public static String decimalFormatMoney(BigDecimal money) {
        DecimalFormat decimalFormat = new DecimalFormat("#,###");
        return decimalFormat.format(money);
    }

    public static boolean isDivisible(BigDecimal dividend, BigDecimal divisor) {
        if (dividend == null || divisor == null) {
            return false;
        }
        BigDecimal remainder = dividend.remainder(divisor);
        return remainder.compareTo(BigDecimal.ZERO) == 0;
    }
}
