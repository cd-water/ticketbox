package com.cdwater.ticketbox.order;

import com.cdwater.ticketbox.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public PageResponse<OrderService.AdminOrderSummary> list(
            @RequestParam(required = false) Long performanceId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orderService.listAdmin(performanceId, status, page, size);
    }

    @GetMapping("/{id}")
    public OrderService.AdminOrderResponse get(@PathVariable Long id) {
        return orderService.getAdmin(id);
    }
}
