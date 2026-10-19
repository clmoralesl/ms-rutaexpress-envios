package cl.rutaexpress.envios.controller;

import cl.rutaexpress.envios.dto.SeguimientoPublicoResponse;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.exception.GlobalExceptionHandler;
import cl.rutaexpress.envios.service.EnvioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EnvioController.class)
@Import(GlobalExceptionHandler.class)
class SeguimientoPublicoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EnvioService envioService;

    @Test
    void seguimientoExistenteDevuelve200SinDatosPrivados() throws Exception {
        String codigo = "RE-TEST-001";

        SeguimientoPublicoResponse respuesta = new SeguimientoPublicoResponse(
                codigo,
                EstadoEnvio.EN_RUTA,
                LocalDateTime.of(2026, 10, 19, 10, 0),
                LocalDateTime.of(2026, 10, 19, 12, 0)
        );

        when(envioService.obtenerSeguimientoPorCodigo(codigo))
                .thenReturn(respuesta);

        mockMvc.perform(get("/api/envios/seguimiento/{codigo}", codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoSeguimiento").value(codigo))
                .andExpect(jsonPath("$.estado").value("EN_RUTA"))
                .andExpect(jsonPath("$.fechaCreacion").exists())
                .andExpect(jsonPath("$.fechaActualizacion").exists())
                .andExpect(jsonPath("$.rutRemitente").doesNotExist())
                .andExpect(jsonPath("$.rutDestinatario").doesNotExist())
                .andExpect(jsonPath("$.direccionOrigen").doesNotExist())
                .andExpect(jsonPath("$.direccionDestino").doesNotExist());
    }

    @Test
    void seguimientoInexistenteDevuelve404() throws Exception {
        String codigo = "RE-NO-EXISTE";

        when(envioService.obtenerSeguimientoPorCodigo(codigo))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No existe un envio con ese codigo de seguimiento"
                ));

        mockMvc.perform(get("/api/envios/seguimiento/{codigo}", codigo))
                .andExpect(status().isNotFound());
    }
}