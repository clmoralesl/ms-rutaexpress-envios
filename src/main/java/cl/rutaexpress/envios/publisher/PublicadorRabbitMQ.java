package cl.rutaexpress.envios.publisher;

import cl.rutaexpress.envios.dto.MensajeEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PublicadorRabbitMQ {

    private static final Logger log = LoggerFactory.getLogger(PublicadorRabbitMQ.class);

    private final RabbitTemplate rabbitTemplate;

    public PublicadorRabbitMQ(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public <T> void publicar(String exchange, String routingKey, MensajeEnvelope<T> mensaje) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, mensaje);
            log.info("Mensaje publicado exitosamente en RabbitMQ [exchange={}, routingKey={}, eventoId={}, tipo={}]",
                    exchange, routingKey, mensaje.eventoId(), mensaje.tipo());
        } catch (Exception ex) {
            // Mecanismo de resiliencia: si RabbitMQ experimenta una interrupcion transitoria,
            // no colapsa la transaccion ni el guardado en base de datos.
            log.error("Fallo de conexion o publicacion transitoria en RabbitMQ [exchange={}, routingKey={}, eventoId={}]: {}. Continuando transaccion.",
                    exchange, routingKey, mensaje.eventoId(), ex.getMessage());
        }
    }
}
