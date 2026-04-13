package jp.co.oda32.aop;

import jp.co.oda32.annotation.ApplicationType;
import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.service.util.LoginUserUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Aspect
@Slf4j
@ApplicationType("web")
public final class ShopCheckAop {

    @Around(value = "execution(* jp.co.oda32.domain.service..*Service.*get*(..)) "
            + "&& !@annotation(jp.co.oda32.annotation.SkipShopCheck) "
            + "&& !@within(jp.co.oda32.annotation.SkipShopCheck)")
    public Object validateGet(ProceedingJoinPoint pj) throws Throwable {
        Object obj = pj.proceed();
        if (!(obj instanceof IEntity entity)) {
            return obj;
        }
        if (entity.getShopNo() == null) {
            return entity;
        }
        Integer loginUserShopNo = LoginUserUtil.getLoginUserInfo().getUser().getShopNo();
        if (loginUserShopNo == null) {
            return null;
        }
        if (loginUserShopNo == 0 || entity.getShopNo().equals(loginUserShopNo)) {
            return entity;
        }
        return null;
    }

    @Around(value = "execution(* jp.co.oda32.domain.service..*Service.*find*(..)) "
            + "&& !@annotation(jp.co.oda32.annotation.SkipShopCheck) "
            + "&& !@within(jp.co.oda32.annotation.SkipShopCheck)")
    @SuppressWarnings("unchecked")
    public List<?> validateFind(ProceedingJoinPoint pj) throws Exception {
        List<?> list;
        try {
            list = (List<?>) pj.proceed();
        } catch (Throwable throwable) {
            log.warn("cast失敗 find:{}", pj.getSignature());
            return null;
        }
        if (list == null) {
            return null;
        }
        if (!list.stream().allMatch(obj -> obj instanceof IEntity)) {
            return list;
        }
        List<? extends IEntity> entityList = (List<? extends IEntity>) list;
        if (entityList.stream().anyMatch(entity -> entity.getShopNo() == null)) {
            return entityList;
        }
        Integer loginUserShopNo = LoginUserUtil.getLoginUserInfo().getUser().getShopNo();
        if (loginUserShopNo == null || loginUserShopNo == -1) {
            return null;
        }
        if (loginUserShopNo == 0) {
            return entityList;
        }
        return entityList.stream()
                .filter(entity -> entity.getShopNo() != null)
                .filter(entity -> entity.getShopNo().equals(loginUserShopNo))
                .collect(Collectors.toList());
    }
}
