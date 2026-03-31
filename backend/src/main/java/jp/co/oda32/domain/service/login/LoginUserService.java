package jp.co.oda32.domain.service.login;

import jp.co.oda32.constant.CompanyType;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.MLoginUser;
import jp.co.oda32.domain.repository.master.LoginUserRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.service.data.LoginUser;
import jp.co.oda32.domain.service.util.LoginUserUtil;
import jp.co.oda32.domain.specification.master.LoginUserSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("loginUserService")
public class LoginUserService extends CustomService implements UserDetailsService {
    @Autowired
    LoginUserRepository loginUserRepository;
    @Autowired
    @Lazy
    PasswordEncoder passwordEncoder;
    private final LoginUserSpecification loginUserSpecification = new LoginUserSpecification();

    public List<MLoginUser> findAll() {
        return loginUserRepository.findAll();
    }

    public MLoginUser get(Integer loginUserNo) {
        return loginUserRepository.findById(loginUserNo).orElseThrow();
    }

    public List<MLoginUser> find(Integer loginUserNo, String userName, String loginId, Flag delFlg) {
        return this.loginUserRepository.findAll(Specification
                .where(this.loginUserSpecification.loginUserNoContains(loginUserNo))
                .and(this.loginUserSpecification.userNameContains(userName))
                .and(this.loginUserSpecification.loginIdContains(loginId))
                .and(this.loginUserSpecification.delFlgContains(delFlg)));
    }

    public MLoginUser getLoginUser() throws Exception {
        LoginUser loginUser = LoginUserUtil.getLoginUserInfo();
        return loginUser.getUser();
    }

    public MLoginUser insert(String loginId, String userName, String password) throws Exception {
        MLoginUser saveLoginUser = new MLoginUser();
        saveLoginUser.setLoginId(loginId);
        saveLoginUser.setUserName(userName);
        String encodedPassword = passwordEncoder.encode(password);
        saveLoginUser.setPassword(encodedPassword);
        saveLoginUser.setCompanyNo(1);
        saveLoginUser.setCompanyType(CompanyType.ADMIN.getValue());
        return this.insert(this.loginUserRepository, saveLoginUser);
    }

    public MLoginUser updatePassword(String loginId, String newPassword) throws Exception {
        MLoginUser saveLoginUser = this.loginUserRepository.findByLoginId(loginId);
        if (newPassword != null) {
            String encodedPassword = passwordEncoder.encode(newPassword);
            saveLoginUser.setPassword(encodedPassword);
        }
        return this.update(this.loginUserRepository, saveLoginUser);
    }

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        MLoginUser user;
        try {
            user = this.loginUserRepository.findByLoginId(loginId);
        } catch (Exception e) {
            throw new UsernameNotFoundException("ユーザー情報の取得に失敗しました");
        }

        if (user == null) {
            throw new UsernameNotFoundException("ユーザーが見つかりません");
        }

        if (Flag.YES.getValue().equals(user.getDelFlg())) {
            throw new UsernameNotFoundException("ユーザーが無効化されています");
        }

        return new LoginUser(user);
    }
}
