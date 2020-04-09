package com.sg.shopping.mapper;

import com.sg.shopping.my.mapper.MyMapper;
import com.sg.shopping.pojo.Items;
import com.sg.shopping.pojo.vo.ItemCommentVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface ItemsMapperCustom extends MyMapper<Items> {

    List<ItemCommentVO> queryItemComments(@Param("paramMap") Map<String, Object> map);

}