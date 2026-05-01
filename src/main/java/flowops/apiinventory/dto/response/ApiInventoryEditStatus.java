package flowops.apiinventory.dto.response;

import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.apiinventory.domain.entity.ApiInventorySource;

public enum ApiInventoryEditStatus {
    AUTO,
    EDITED;

    public static ApiInventoryEditStatus from(ApiInventory apiInventory) {
        if (apiInventory.getSourceType() == ApiInventorySource.MANUAL || hasEditHistory(apiInventory)) {
            return EDITED;
        }
        return AUTO;
    }

    private static boolean hasEditHistory(ApiInventory apiInventory) {
        return apiInventory.getCreatedAt() != null
                && apiInventory.getUpdatedAt() != null
                && apiInventory.getUpdatedAt().isAfter(apiInventory.getCreatedAt());
    }
}
