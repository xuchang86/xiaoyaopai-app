﻿drop table if exists T_MALL_GOODS;

/*==============================================================*/
/* Table: T_MALL_GOODS                                          */
/*==============================================================*/
create table T_MALL_GOODS
(
   id                   int not null auto_increment comment '主键',
   name                 varchar(20) comment '商品名称',
   description          varchar(500) comment '商品描述',
   number               varchar(20) comment '商品编码',
   type_id              int comment '商品类型id',
   price                decimal(10,2) comment '商品价格',
   user_id              int comment '创建用户id',
   create_date          timestamp comment '创建日期',
   url                  text comment '商品图片',
   area                 varchar(20) comment '商品地区',
   is_sale              boolean comment '是否出售',
   seller_id            int comment '卖家id',
   vip_price            decimal(10,2) comment '会员价格',
   level                int comment '商品等级',
   model                varchar(500) comment '商品型号',
   freight              decimal(10,2) comment '运费',
   primary key (id)
);

alter table T_MALL_GOODS comment '商城商品';
