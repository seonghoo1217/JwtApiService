package learn.jwt.andsocket.service.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import learn.jwt.andsocket.repository.MemberRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;

@Transactional
@Service
@RequiredArgsConstructor
@Setter(value = AccessLevel.PRIVATE)
public class JwtServiceImpl implements JwtService{

    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.access.expiration}")
    private long accessTokenValidityInSeconds;
    @Value("${jwt.refresh.expiration}")
    private long refreshTokenValidityInSeconds;
    @Value("${jwt.access.header}")
    private String accessHeader;
    @Value("${jwt.refresh.header}")
    private String refreshHeader;

    private static String SECRET_KEY = "";

    @PostConstruct
    public void secretKeyEncode() throws UnsupportedEncodingException {
        byte[] secretBytes = secret.getBytes("UTF-8");
        Base64.Encoder encoder = Base64.getEncoder();
        byte[] enCodeBytes = encoder.encode(secretBytes);
        SECRET_KEY = new String(enCodeBytes);
    }

    private static final String ACCESS_TOKEN_SUBJECT = "AccessToken";
    private static final String REFRESH_TOKEN_SUBJECT = "RefreshToken";
    private static final String USERNAME_CLAIM = "username";
    private static final String BEARER = "Bearer ";


    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;




    @Override
    public String createAccessToken(String username) {
        return JWT.create()
                .withSubject(ACCESS_TOKEN_SUBJECT)
                .withExpiresAt(new Date(System.currentTimeMillis() + accessTokenValidityInSeconds * 1000))
                .withClaim(USERNAME_CLAIM, username)
                .sign(Algorithm.HMAC512(SECRET_KEY));
    }

    @Override
    public String createRefreshToken() {
        return JWT.create()
                .withSubject(REFRESH_TOKEN_SUBJECT)
                .withExpiresAt(new Date(System.currentTimeMillis() + refreshTokenValidityInSeconds * 1000))
                .sign(Algorithm.HMAC512(SECRET_KEY));
    }

    @Override
    public void updateRefreshToken(String username, String refreshToken) {
        memberRepository.findByUsername(username)
                .ifPresentOrElse(
                        member -> member.changeRefreshToken(refreshToken),
                        () -> new Exception("회원이 없습니다")
                );
    }



    @Override
    public void destroyRefreshToken(String username) {
        memberRepository.findByUsername(username)
                .ifPresentOrElse(
                        member -> member.destroyRefreshToken(),
                        () -> new Exception("회원이 없습니다")
                );
    }

    @SneakyThrows
    @Override
    public void sendToken(HttpServletResponse response, String accessToken, String refreshToken) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);

        setAccessTokenHeader(response, accessToken);
        setRefreshTokenHeader(response, refreshToken);


        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put(ACCESS_TOKEN_SUBJECT, accessToken);
        tokenMap.put(REFRESH_TOKEN_SUBJECT, refreshToken);

        String token = objectMapper.writeValueAsString(tokenMap);

        response.getWriter().write(token);
    }


    @Override
    public String extractAccessToken(HttpServletRequest request) throws IOException, ServletException {
        return Optional.ofNullable(request.getHeader(accessHeader)).map(accessToken -> accessToken.replace(BEARER, "")).orElse(null);
    }

    @Override
    public String extractRefreshToken(HttpServletRequest request) throws IOException, ServletException {
        return Optional.ofNullable(request.getHeader(refreshHeader)).map(refreshToken -> refreshToken.replace(BEARER, "")).orElse(null);
    }

    @Override
    public String extractUsername(String accessToken) {
        return JWT.require(Algorithm.HMAC512(SECRET_KEY)).build().verify(accessToken).getClaim(USERNAME_CLAIM).asString();

    }

    @Override
    public void setAccessTokenHeader(HttpServletResponse response, String accessToken) {
        response.setHeader(accessHeader, accessToken);
    }

    @Override
    public void setRefreshTokenHeader(HttpServletResponse response, String refreshToken) {
        response.setHeader(refreshHeader, refreshToken);
    }
}