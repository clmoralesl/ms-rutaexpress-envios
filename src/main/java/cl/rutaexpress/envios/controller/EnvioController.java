package cl.rutaexpress.envios.controller;

import cl.rutaexpress.envios.dto.ActualizarEstadoRequest;
import cl.rutaexpress.envios.dto.CrearEnvioRequest;
import cl.rutaexpress.envios.dto.EnvioResponse;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.service.EnvioService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/envios")
public class EnvioController {

    private final EnvioService envioService;

    public EnvioController(EnvioService envioService) {
        this.envioService = envioService;
    }

    @PostMapping
    public ResponseEntity<EnvioResponse> crear(
            @Valid @RequestBody CrearEnvioRequest request) {

        EnvioResponse response = envioService.crear(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnvioResponse> obtenerPorId(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                envioService.obtenerPorId(id)
        );
    }

    @GetMapping
    public ResponseEntity<List<EnvioResponse>> listar(
            @RequestParam(required = false) EstadoEnvio estado,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime fechaDesde,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime fechaHasta) {

        return ResponseEntity.ok(
                envioService.listar(
                        estado,
                        fechaDesde,
                        fechaHasta
                )
        );
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<EnvioResponse> actualizarEstado(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarEstadoRequest request) {

        return ResponseEntity.ok(
                envioService.actualizarEstado(
                        id,
                        request.estado()
                )
        );
    }
}