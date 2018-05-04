package com.ok.okhelper.service.impl;

import com.ok.okhelper.dao.*;
import com.ok.okhelper.exception.IllegalException;
import com.ok.okhelper.exception.NotFoundException;
import com.ok.okhelper.pojo.bo.IdAndNameBo;
import com.ok.okhelper.pojo.bo.StorageDetailBo;
import com.ok.okhelper.pojo.constenum.ConstEnum;
import com.ok.okhelper.pojo.dto.StorageDetailDto;
import com.ok.okhelper.pojo.dto.StorageOrderDto;
import com.ok.okhelper.pojo.po.*;
import com.ok.okhelper.pojo.vo.StorageOrderVo;
import com.ok.okhelper.service.StockService;
import com.ok.okhelper.service.StorageOrderService;
import com.ok.okhelper.shiro.JWTUtil;
import com.ok.okhelper.until.NumberGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/*
*Author:zhangxin_an
*Description:
*Data:Created in 10:53 2018/5/3
*/
@Service
@Slf4j
public class StorageOrderServiceImpl implements StorageOrderService {
	
	@Autowired
	private StorageOrderMapper storageOrderMapper;
	
	@Autowired
	private StorageOrderDetailMapper storageOrderDetailMapper;
	
	@Autowired
	private UserMapper userMapper;
	
	@Autowired
	private WarehouseMapper warehouseMapper;
	
	@Autowired
	private SupplierMapper supplierMapper;
	
	@Autowired
	ProductMapper productMapper;
	
	@Autowired
	StockService stockService;
	
	
	@Override
	public void insertStorage(StorageOrderDto storageOrderDto) {
		
		log.info("Enter method insertStorage params:" + storageOrderDto);
		checkStorageOrderDto(storageOrderDto);
		storageOrderDto.setOrderNumber(NumberGenerator.generatorPlaceOrderNumber(12, JWTUtil.getUserId()));
		
		//添加入库单
		StorageOrder storageOrder = new StorageOrder();
		BeanUtils.copyProperties(storageOrderDto, storageOrder);
		storageOrder.setStoreId(JWTUtil.getStoreId());
		storageOrderMapper.insertSelective(storageOrder);
		
		List<StorageDetailDto> storageDetailDtos = storageOrderDto.getStorageDetail();
		
		//遍历将订单子项插入数据库
		storageDetailDtos.forEach(storageDetailDto -> {
			StorageOrderDetail storageOrderDetail = new StorageOrderDetail();
			BeanUtils.copyProperties(storageDetailDto, storageOrderDetail);
			storageOrderDetail.setStorageInId(storageOrder.getId());
			
			// 增加库存(分别于库存表商品表修改)
			Stock stock = new Stock();
			stock.setProductDate(storageDetailDto.getProductDate());
			stock.setProductId(storageDetailDto.getProductId());
			stock.setWarehouseId(storageDetailDto.getWarehouseId());
			stockService.updateOrAddStockNumber(stock,storageDetailDto.getStorageCount());
			
			productMapper.addSalesStock(storageDetailDto.getStorageCount(),storageDetailDto.getProductId());
			
			
			storageOrderDetailMapper.insertSelective(storageOrderDetail);
		});
		
//		StorageOrderVo storageOrderVo = getStorageOrderByOrderNumber(storageOrder.getOrderNumber());
		
		
		log.info("Exit method insertStorage params:" + storageOrderDto);
		
		
//		return storageOrderVo;
	}
	
	/*
	* @Author zhangxin_an 
	* @Date 2018/5/3 17:27
	* @Params [storageOrderDto]  
	* @Return boolean  
	* @Description:检查入库参数
	*/  
	private boolean checkStorageOrderDto(StorageOrderDto storageOrderDto){
		
		List<StorageDetailDto> storageDetailDtos = storageOrderDto.getStorageDetail();
		if (CollectionUtils.isEmpty(storageDetailDtos)) {
			throw new IllegalException("子项为空");
		}
		storageDetailDtos.forEach(storageDetailDto -> {
			//子项参数不为空
			if (storageDetailDto.getProductDate() == null
					|| storageDetailDto.getShelfLife() == null
					|| storageDetailDto.getProductId() == null
					|| storageDetailDto.getStorageCount() == null
					|| storageDetailDto.getStoragePrice() == null
					|| storageDetailDto.getProductId() == null
					|| storageDetailDto.getWarehouseId() == null) {
				throw new IllegalException("参数不完整");
			}
			
			Warehouse warehouse = warehouseMapper.selectByPrimaryKey(storageDetailDto.getWarehouseId());
			if(warehouse == null || warehouse.getDeleteStatus() == ConstEnum.STATUSENUM_UNAVAILABLE.getCode())
				throw new IllegalException("所选仓库不可用");
			
			Product product = productMapper.selectByPrimaryKey(storageDetailDto.getProductId());
			if(product == null || product.getDeleteStatus() == ConstEnum.STATUSENUM_UNAVAILABLE.getCode())
				throw new IllegalException("商品不存在");
			
		});
			
			return true;
	}
	
	
	/*
	* @Author zhangxin_an 
	* @Date 2018/5/3 18:55
	* @Params [orderNumber]  
	* @Return com.ok.okhelper.pojo.vo.StorageOrderVo  
	* @Description:获取订单
*/
	@Override
	public StorageOrderVo getStorageOrderByOrderNumber(String orderNumber) {
		log.info("Enter method getStorageOrderByOrderNUmber params:" + orderNumber);
		
		if (StringUtils.isBlank(orderNumber)) {
			throw new IllegalException("参数错误");
		}
		
		//前端订单数据
		StorageOrderVo storageOrderVo = new StorageOrderVo();
		StorageOrder storageOrder = storageOrderMapper.getStorageOrderByOrderNumber(orderNumber);
		if(storageOrder == null){
			throw new NotFoundException("未找到该订单");
		}
		BeanUtils.copyProperties(storageOrder, storageOrderVo);
		
		//获取子项
		List<StorageDetailBo> storageDetailBoList = getStorageDetailBo(storageOrder.getId());
		storageOrderVo.setStorageDetail(storageDetailBoList);
		
		//转换入库员，供应商id到name,前端识别
		
		IdAndNameBo stockiner = userMapper.getIdAndName(storageOrder.getStockiner());
		IdAndNameBo supplier = supplierMapper.getIdAndName(storageOrder.getSupplierId());
		storageOrderVo.setStockiner(stockiner);
		storageOrderVo.setSupplier(supplier);
		
		
		log.info("Exit method getStorageOrderByOrderNUmber params:" + storageOrderVo);
		return storageOrderVo;
	}
	
	/*
	* @Author zhangxin_an 
	* @Date 2018/5/3 14:03
	* @Params [id]  
	* @Return java.util.List<com.ok.okhelper.pojo.bo.StorageDetailBo>  
	* @Description:订单Id获取子项
	*/
	private List<StorageDetailBo> getStorageDetailBo(Long id) {
		
		log.info("Enter method getStorageDetailBo params:" + id);
		
		//获取入货单子项
		List<StorageOrderDetail> storageOrderDetailList = storageOrderDetailMapper.getStorageOrderDetailByOrderId(id);
		if(CollectionUtils.isEmpty(storageOrderDetailList)){
			throw new IllegalException("子项为空");
		}
		
		//前端展示子项数据
		List<StorageDetailBo> storageDetailBoList = new ArrayList<>(storageOrderDetailList.size());
		
		IdAndNameBo warehouse;
		IdAndNameBo product;
		StorageDetailBo storageDetailBo;
		for(StorageOrderDetail storageOrderDetail : storageOrderDetailList){
			warehouse = warehouseMapper.getIdAndName(storageOrderDetail.getWarehouseId());
			product = productMapper.getIdAndName(storageOrderDetail.getProductId());
			storageDetailBo = new StorageDetailBo();
			BeanUtils.copyProperties(storageOrderDetail, storageDetailBo);
			storageDetailBo.setWarehouse(warehouse);
			storageDetailBo.setProduct(product);
			storageDetailBoList.add(storageDetailBo);
		}
		
		log.info("Exit method getStorageDetailBo params:" + storageDetailBoList);
		return storageDetailBoList;
	}
	
	
}
