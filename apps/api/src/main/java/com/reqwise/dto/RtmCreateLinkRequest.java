package com.reqwise.dto;

import java.util.UUID;

public record RtmCreateLinkRequest(
        UUID sourceReqId,
        UUID targetReqId,
        String linkType // REFINES, DEPENDS_ON, CONSTRAINS, VERIFIED_BY
) {}
