package cl.rutaexpress.envios.publisher;

import cl.rutaexpress.envios.dto.MensajeEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicadorRabbitMQTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private PublicadorRabbitMQ publicador;

    @BeforeEach
    void setUp() {
        publicador = new PublicadorRabbitMQ(rabbitTemplate);
    }

    @Test
    @DisplayName("Debe publicar mensaje exitosamente cuando RabbitMQ esta disponible")
    void testPublicarExito() {
        MensajeEnvelope<String> mensaje = MensajeEnvelope.de("TEST_TIPO", "datos-prueba");

        publicador.publicar("cmd.direct", "email.send", mensaje);

        verify(rabbitTemplate, times(1)).convertAndSend("cmd.direct", "email.send", mensaje);
    }

    @Test
    @DisplayName("Mecanismo de resiliencia: No debe propagar excepcion si RabbitMQ falla o no esta disponible")
    void testPublicarResilienciaFallaRabbitMQ() {
        MensajeEnvelope<String> mensaje = MensajeEnvelope.de("TEST_TIPO", "datos-prueba");

        doThrow(new AmqpConnectException(new ConnectException("Conexion rechazada a RabbitMQ")))
                .when(rabbitTemplate).convertAndSend(eq("cmd.direct"), eq("email.send"), any(MensajeEnvelope.class));

        assertDoesNotThrow(() -> publicador.publicar("cmd.direct", "email.send", mensaje));

        verify(rabbitTemplate, times(1)).convertAndSend("cmd.direct", "email.send", mensaje);
    }

    private static class ConnectException extends java.net.ConnectException {
        ConnectException(String msg) {
            super(msg);
        }
    }
}
