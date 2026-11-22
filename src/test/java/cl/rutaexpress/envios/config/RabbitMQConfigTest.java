package cl.rutaexpress.envios.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RabbitMQConfigTest {

    @Test
    @DisplayName("C7: el productor declara solo cmd.direct, sin colas ni bindings")
    void soloDeclaraExchangeDirectoSinColas() {
        new ApplicationContextRunner()
                .withUserConfiguration(RabbitMQConfig.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .run(ctx -> {
                    Map<String, Exchange> exchanges = ctx.getBeansOfType(Exchange.class);
                    assertEquals(1, exchanges.size());
                    Exchange exchange = exchanges.values().iterator().next();
                    assertEquals("cmd.direct", exchange.getName());
                    assertEquals("direct", exchange.getType());
                    assertTrue(exchange.isDurable());
                    assertFalse(exchange.isAutoDelete());
                    assertFalse(exchange.isInternal());
                    assertEquals(0, ctx.getBeansOfType(Queue.class).size());
                    assertEquals(0, ctx.getBeansOfType(Binding.class).size());
                });
    }
}
