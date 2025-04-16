package com.cdwater.ticketbox.order;

import com.cdwater.ticketbox.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderCommand command) {
        OrderService.CreateResult result = orderService.create(authentication.getName(), idempotencyKey, command);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.order());
    }

    @GetMapping
    public PageResponse<OrderResponse> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orderService.list(authentication.getName(), page, size);
    }

    @GetMapping("/{id}")
    public OrderResponse get(Authentication authentication, @PathVariable Long id) {
        return orderService.get(authentication.getName(), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(Authentication authentication, @PathVariable Long id) {
        return orderService.cancel(authentication.getName(), id);
    }
}
