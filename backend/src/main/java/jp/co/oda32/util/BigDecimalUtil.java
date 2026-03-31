package jp.co.oda32.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.DecimalFormat;

/**
 * BigDecimal用Utilクラス
 *
 * @author k_oda
 * @since 2018/08/10
 */
public class BigDecimalUtil {
    public static final BigDecimal MAX_INT8_DECIMAL4 = new BigDecimal("99999999.9999");
    public static final BigDecimal MAX_INT8_DECIMAL2 = new BigDecimal("99999999.99");

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
        return value.setScale(round - 1, BigDecimal.ROUND_UP);
    }

    public static BigDecimal roundDown(BigDecimal value, int round) {
        value = convertNullToZero(value);
        return value.setScale(round - 1, BigDecimal.ROUND_DOWN);
    }

    public static BigDecimal roundHalfUp(BigDecimal value, int round) {
        value = convertNullToZero(value);
        return value.setScale(round - 1, BigDecimal.ROUND_HALF_UP);
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
