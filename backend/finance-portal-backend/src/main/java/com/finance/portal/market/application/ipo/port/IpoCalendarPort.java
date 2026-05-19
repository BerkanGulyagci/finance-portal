package com.finance.portal.market.application.ipo.port;

import com.finance.portal.market.application.ipo.IpoItem;

import java.util.List;

/**
 * Halka arz takvimi (halkarz.com) adaptör portu.
 */
public interface IpoCalendarPort {

    List<IpoItem> fetchIpos();
}
