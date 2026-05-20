package com.finance.portal.common.application.logging;

public final class BusinessLogSupport {

    public static final String SERVICE_NAME = "finance-portal-backend";
    public static final String CATEGORY_AUDIT = "AUDIT";
    public static final String CATEGORY_BUSINESS = "BUSINESS";

    public static final String EVENT_USER_REGISTERED = "USER_REGISTERED";
    public static final String EVENT_PROFILE_UPDATED = "PROFILE_UPDATED";
    public static final String EVENT_EMAIL_CHANGED = "EMAIL_CHANGED";
    public static final String EVENT_PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String EVENT_USER_BANNED = "USER_BANNED";
    public static final String EVENT_USER_UNBANNED = "USER_UNBANNED";
    public static final String EVENT_LDAP_USER_CREATED = "LDAP_USER_CREATED";
    public static final String EVENT_EMAIL_VERIFICATION_REQUESTED = "EMAIL_VERIFICATION_REQUESTED";
    public static final String EVENT_USER_ROLE_ASSIGNED = "USER_ROLE_ASSIGNED";
    public static final String EVENT_USER_SESSIONS_REVOKED = "USER_SESSIONS_REVOKED";
    public static final String EVENT_TEMP_BAN_EXPIRED = "TEMP_BAN_EXPIRED";

    public static final String EVENT_PORTFOLIO_CREATED = "PORTFOLIO_CREATED";
    public static final String EVENT_PORTFOLIO_UPDATED = "PORTFOLIO_UPDATED";
    public static final String EVENT_WATCHLIST_PORTFOLIO_CREATED = "WATCHLIST_PORTFOLIO_CREATED";
    public static final String EVENT_PORTFOLIO_DELETED = "PORTFOLIO_DELETED";
    public static final String EVENT_TRANSACTION_ADDED = "TRANSACTION_ADDED";
    public static final String EVENT_TRANSACTION_DELETED = "TRANSACTION_DELETED";
    public static final String EVENT_WATCHLIST_ITEM_ADDED = "WATCHLIST_ITEM_ADDED";
    public static final String EVENT_WATCHLIST_ITEM_REMOVED = "WATCHLIST_ITEM_REMOVED";

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_BAN = "BAN";
    public static final String ACTION_UNBAN = "UNBAN";
    public static final String ACTION_REGISTER = "REGISTER";
    public static final String ACTION_ASSIGN = "ASSIGN";
    public static final String ACTION_REVOKE = "REVOKE";

    public static final String TRIGGER_PASSWORD_CHANGE = "password_change";

    private BusinessLogSupport() {}
}
