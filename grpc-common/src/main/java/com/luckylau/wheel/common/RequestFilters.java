package com.luckylau.wheel.common;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class RequestFilters {

    List<AbstractRequestFilter> filters = new ArrayList<AbstractRequestFilter>();

    public void registerFilter(AbstractRequestFilter requestFilter) {
        filters.add(requestFilter);
    }

}
