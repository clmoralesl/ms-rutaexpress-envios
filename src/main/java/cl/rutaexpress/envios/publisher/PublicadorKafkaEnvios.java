package cl.rutaexpress.envios.publisher;

import cl.rutaexpress.envios.config.KafkaConfig;
import cl.rutaexpress.envios.dto.EventoEnvioKafka;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class PublicadorKafkaEnvios {

    private static final Logger log = LoggerFactory.getLogger(PublicadorKafkaEnvios.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topicoShipmentsEvents;

    public PublicadorKafkaEnvios(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${kafka.topic.shipments-events:" + KafkaConfig.TOPICO_SHIPMENTS_EVENTS + "}") String topicoShipmentsEvents) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicoShipmentsEvents = topicoShipmentsEvents;
    }

    public void publicarEvento(EventoEnvioKafka evento) {
        if (evento == null) {
            log.warn("Se intento publicar un evento nulo en Kafka.");
            return;
        }

        String clave = evento.envioId() != null ? String.valueOf(evento.envioId()) : "0";

        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topicoShipmentsEvents, clave, evento);

            future.whenComplete((resultado, excepcion) -> {
                if (excepcion != null) {
                    log.error("Fallo asincrono al publicar evento en Kafka [topico={}, clave={}, eventoId={}]: {}",
                            topicoShipmentsEvents, clave, evento.eventoId(), excepcion.getMessage());
                } else if (resultado != null && resultado.getRecordMetadata() != null) {
                    log.info("Evento publicado exitosamente en Kafka [topico={}, particion={}, offset={}, clave={}, eventoId={}, estadoNuevo={}]",
                            topicoShipmentsEvents,
                            resultado.getRecordMetadata().partition(),
                            resultado.getRecordMetadata().offset(),
                            clave,
                            evento.eventoId(),
                            evento.estadoNuevo());
                }
            });
        } catch (Exception ex) {
            // Resiliencia operativa: no colapsar la transaccion de base de datos ni bloquear la respuesta HTTP
            log.error("Fallo sincronico al enviar mensaje a Kafka [topico={}, clave={}, eventoId={}]: {}. Continuando transaccion de persistencia.",
                    topicoShipmentsEvents, clave, evento.eventoId(), ex.getMessage());
        }
    }
}
