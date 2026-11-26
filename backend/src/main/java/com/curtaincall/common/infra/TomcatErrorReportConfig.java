package com.curtaincall.common.infra;

import java.util.Arrays;

import org.apache.catalina.Pipeline;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.boot.tomcat.ConfigurableTomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Tomcat Host의 오류 보고 밸브를 {@link ProblemDetailErrorReportValve}로 바꾼다.
 *
 * <p>Boot는 컨텍스트 커스터마이저로 Host 파이프라인에 기본 ErrorReportValve를 넣는다. 이 커스터마이저는 가장 늦게 등록되므로
 * 그 뒤에 실행돼, 기본 밸브를 빼고 이 밸브를 넣는다. 밸브가 둘이면 안쪽 것이 먼저 HTML을 쓰기 때문이다.
 * Host가 시작할 때 기본 밸브를 다시 더하지 않도록 Host의 errorReportValveClass도 이 밸브로 바꾼다.
 */
@Configuration(proxyBeanMethods = false)
public class TomcatErrorReportConfig implements WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory>, Ordered {

    @Override
    public void customize(ConfigurableTomcatWebServerFactory factory) {
        factory.addContextCustomizers(context -> {
            // Host는 시작할 때 errorReportValveClass와 클래스 이름이 같은 밸브가 없으면 그 밸브(기본 HTML)를 더한다.
            if (context.getParent() instanceof StandardHost host) {
                host.setErrorReportValveClass(ProblemDetailErrorReportValve.class.getName());
            }
            Pipeline hostPipeline = context.getParent().getPipeline();
            Arrays.stream(hostPipeline.getValves())
                    .filter(valve -> valve instanceof ErrorReportValve && !(valve instanceof ProblemDetailErrorReportValve))
                    .forEach(hostPipeline::removeValve);
            boolean installed = Arrays.stream(hostPipeline.getValves()).anyMatch(ProblemDetailErrorReportValve.class::isInstance);
            if (!installed) {
                hostPipeline.addValve(new ProblemDetailErrorReportValve());
            }
        });
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
