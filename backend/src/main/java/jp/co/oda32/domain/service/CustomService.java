package jp.co.oda32.domain.service;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.service.data.LoginUser;
import jp.co.oda32.domain.service.util.LoginUserUtil;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.DateTimeUtil;
import lombok.Setter;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public abstract class CustomService {
    @Setter
    protected Integer batchId;

    @SuppressWarnings("unchecked")
    protected <T extends JpaRepository & JpaSpecificationExecutor, U extends IEntity> List<U> defaultFindAll(T repository) {
        return repository.findAll(Specification
                .where(new CommonSpecification().delFlgContains(Flag.NO)));
    }

    @SuppressWarnings("unchecked")
    protected <T extends JpaRepository, U extends IEntity> U insert(T repository, U entity) throws Exception {
        LoginUser loginUser = null;
        try {
            loginUser = LoginUserUtil.getLoginUserInfo();
            entity.setAddUserNo(loginUser.getUser().getLoginUserNo());
        } catch (Exception e) {
            entity.setAddUserNo(this.batchId);
        }
        validateUpdateByShop(entity, loginUser == null ? 0 : loginUser.getUser().getShopNo());
        entity.setAddDateTime(DateTimeUtil.getNow());
        entity.setDelFlg(entity.getDelFlg() == null ? Flag.NO.getValue() : entity.getDelFlg());
        return (U) repository.save(entity);
    }

    @SuppressWarnings("unchecked")
    protected <T extends JpaRepository, U extends IEntity> U update(T repository, U entity) throws Exception {
        LoginUser loginUser = null;
        try {
            loginUser = LoginUserUtil.getLoginUserInfo();
            entity.setModifyUserNo(loginUser.getUser().getLoginUserNo());
        } catch (Exception e) {
            entity.setModifyUserNo(this.batchId);
        }
        validateUpdateByShop(entity, loginUser == null ? 0 : loginUser.getUser().getShopNo());
        entity.setModifyDateTime(DateTimeUtil.getNow());
        entity.setDelFlg(entity.getDelFlg() == null ? Flag.NO.getValue() : entity.getDelFlg());
        return (U) repository.save(entity);
    }

    @SuppressWarnings("unchecked")
    protected <T extends JpaRepository, U extends IEntity> U delete(T repository, U entity) throws Exception {
        LoginUser loginUser = null;
        try {
            loginUser = LoginUserUtil.getLoginUserInfo();
            entity.setModifyUserNo(loginUser.getUser().getLoginUserNo());
        } catch (Exception e) {
            entity.setModifyUserNo(this.batchId);
        }
        validateUpdateByShop(entity, loginUser == null ? 0 : loginUser.getUser().getShopNo());
        entity.setModifyDateTime(DateTimeUtil.getNow());
        entity.setDelFlg(Flag.YES.getValue());
        return (U) repository.save(entity);
    }

    @SuppressWarnings("unchecked")
    protected <T extends JpaRepository, U extends IEntity> void deletePermanently(T repository, U entity) throws Exception {
        repository.delete(entity);
    }

    private <U extends IEntity> void validateUpdateByShop(U entity, Integer shopNo) throws Exception {
        Integer entityShopNo = entity.getShopNo();
        if (entityShopNo == null) return;
        if (shopNo == null) throw new Exception("ショップユーザでないので操作できません。");
        if (shopNo == 0) return;
        if (!shopNo.equals(entityShopNo)) throw new Exception("自分のショップ以外のレコードは操作できません。");
    }
}
