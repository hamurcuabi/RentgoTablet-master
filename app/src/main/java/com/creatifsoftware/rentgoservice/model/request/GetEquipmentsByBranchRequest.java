package com.creatifsoftware.rentgoservice.model.request;

import com.creatifsoftware.rentgoservice.model.base.BaseRequest;

/**
 * Created by kerembalaban on 17.02.2019 at 23:33.
 */
public class GetEquipmentsByBranchRequest extends BaseRequest {
    public String branchId;

    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }
}
