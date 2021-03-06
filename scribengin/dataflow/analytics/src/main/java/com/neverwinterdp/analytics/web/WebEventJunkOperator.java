package com.neverwinterdp.analytics.web;

import com.neverwinterdp.message.Message;
import com.neverwinterdp.scribengin.dataflow.DataStreamOperator;
import com.neverwinterdp.scribengin.dataflow.DataStreamOperatorContext;
import com.neverwinterdp.util.JSONSerializer;

public class WebEventJunkOperator extends DataStreamOperator {
  @Override
  public void process(DataStreamOperatorContext ctx, Message record) throws Exception {
    WebEvent webEvent = JSONSerializer.INSTANCE.fromBytes(record.getData(), WebEvent.class) ;
    ctx.write(record);
  }
}