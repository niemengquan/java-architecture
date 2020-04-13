package com.sg.shopping.service.impl.center;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.sg.shopping.common.enums.OrderStatusEnum;
import com.sg.shopping.common.enums.YesOrNo;
import com.sg.shopping.common.utils.PagedGridResult;
import com.sg.shopping.mapper.OrderStatusMapper;
import com.sg.shopping.mapper.OrdersMapper;
import com.sg.shopping.mapper.OrdersMapperCustom;
import com.sg.shopping.pojo.OrderStatus;
import com.sg.shopping.pojo.Orders;
import com.sg.shopping.pojo.vo.MyOrdersVO;
import com.sg.shopping.service.center.MyOrdersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MyOrdersServiceImpl implements MyOrdersService {

    @Autowired
    OrdersMapper ordersMapper;

    @Autowired
    OrdersMapperCustom ordersMapperCustom;

    @Autowired
    OrderStatusMapper orderStatusMapper;

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public PagedGridResult queryMyOrders(String userId, Integer orderStatus, Integer page, Integer pageSize) {

        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        if (orderStatus != null) {
            map.put("orderStatus", orderStatus);
        }

        PageHelper.startPage(page, pageSize);
        List<MyOrdersVO> list = ordersMapperCustom.queryMyOrders(map);

        return setterPagedGrid(list, page);
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public OrderStatus updateDeliverOrderStatus(String orderId) {
        OrderStatus orderStatus = new OrderStatus();
        orderStatus.setOrderStatus(OrderStatusEnum.WAIT_RECEIVE.type);
        orderStatus.setDeliverTime(new Date());

        Example example = new Example(OrderStatus.class);
        example.createCriteria()
                .andEqualTo("orderId", orderId)
                .andEqualTo("orderStatus", OrderStatusEnum.WAIT_DELIVER.type);

        orderStatusMapper.updateByExample(orderStatus, example);

        return orderStatusMapper.selectByPrimaryKey(orderId);
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public Orders queryOrders(String userId, String orderId) {
        Orders orders = new Orders();
        orders.setId(orderId);
        orders.setUserId(userId);
        orders.setIsDelete(YesOrNo.NO.type);
        return ordersMapper.selectOne(orders);
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public OrderStatus updateConfirmReceiveOrderStatus(String orderId) {
        OrderStatus orderStatus = new OrderStatus();
        orderStatus.setOrderStatus(OrderStatusEnum.SUCCESS.type);
        orderStatus.setSuccessTime(new Date());

        Example example = new Example(OrderStatus.class);
        example.createCriteria()
                .andEqualTo("orderId", orderId)
                .andEqualTo("orderStatus", OrderStatusEnum.WAIT_RECEIVE.type);

        orderStatusMapper.updateByExample(orderStatus, example);

        return orderStatusMapper.selectByPrimaryKey(orderId);
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public boolean updateDeleteOrderStatus(String userId, String orderId) {
        Orders orders = new Orders();
        orders.setIsDelete(YesOrNo.YES.type);
        orders.setUpdatedTime(new Date());

        Example example = new Example(Orders.class);
        example.createCriteria()
                .andEqualTo("id", orderId)
                .andEqualTo("userId", userId);

        int rowUpdated = ordersMapper.updateByExampleSelective(orders, example);
        return rowUpdated == 1;
    }


    private PagedGridResult setterPagedGrid(List<?> list, Integer page) {
        PageInfo<?> pageList = new PageInfo<>(list);
        PagedGridResult pagedGridResult = new PagedGridResult();
        pagedGridResult.setPage(page);
        pagedGridResult.setRows(list);
        pagedGridResult.setTotal(pageList.getPages());
        pagedGridResult.setRecords(pageList.getTotal());

        return pagedGridResult;
    }
}
