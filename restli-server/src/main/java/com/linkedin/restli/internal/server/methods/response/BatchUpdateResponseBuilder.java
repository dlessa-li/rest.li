/*
   Copyright (c) 2012 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package com.linkedin.restli.internal.server.methods.response;


import com.linkedin.data.DataMap;
import com.linkedin.data.template.SetMode;
import com.linkedin.r2.message.rest.RestRequest;
import com.linkedin.restli.common.BatchResponse;
import com.linkedin.restli.common.ErrorResponse;
import com.linkedin.restli.common.ProtocolVersion;
import com.linkedin.restli.common.UpdateStatus;
import com.linkedin.restli.internal.common.URIParamUtils;
import com.linkedin.restli.internal.server.RoutingResult;
import com.linkedin.restli.internal.server.ServerResourceContext;
import com.linkedin.restli.internal.server.methods.AnyRecord;
import com.linkedin.restli.server.BatchUpdateResult;
import com.linkedin.restli.server.RestLiServiceException;
import com.linkedin.restli.server.UpdateResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * @author Josh Walker
 * @version $Revision: $
 */

public final class BatchUpdateResponseBuilder implements RestLiResponseBuilder
{
  private final ErrorResponseBuilder _errorResponseBuilder;

  public BatchUpdateResponseBuilder(ErrorResponseBuilder errorResponseBuilder)
  {
    _errorResponseBuilder = errorResponseBuilder;
  }

  @Override
  public PartialRestResponse buildResponse(final RestRequest request,
                                           final RoutingResult routingResult,
                                           final Object object,
                                           final Map<String, String> headers)
      throws IOException
  {
    @SuppressWarnings({ "unchecked" })
    /** constrained by signature of {@link com.linkedin.restli.server.resources.CollectionResource#batchUpdate(java.util.Map)} */
    final BatchUpdateResult<Object, ?> updateResult = (BatchUpdateResult<Object, ?>) object;

    final Map<Object, UpdateResponse> updates = updateResult.getResults();
    final Map<Object, RestLiServiceException> serviceErrors = updateResult.getErrors();

    final ServerResourceContext context = (ServerResourceContext) routingResult.getContext();
    final ProtocolVersion protocolVersion = context.getRestliProtocolVersion();

    final Map<Object, ErrorResponse> errors = BatchResponseUtil.populateErrors(serviceErrors, context, _errorResponseBuilder);

    final Set<Object> mergedKeys = new HashSet<Object>(updates.keySet());
    mergedKeys.addAll(errors.keySet());

    final Map<Object, UpdateStatus> results = new HashMap<Object, UpdateStatus>((int) Math.ceil(mergedKeys.size() / 0.75));

    for (Object key : mergedKeys)
    {
      final UpdateStatus status = new UpdateStatus();

      final UpdateResponse update = updates.get(key);
      if (update != null)
      {
        status.setStatus(update.getStatus().getCode());
      }

      status.setError(errors.get(key), SetMode.IGNORE_NULL);

      results.put(key, status);
    }

    // filter

    final BatchResponse<AnyRecord> response = toBatchResponse(results, protocolVersion);
    return new PartialRestResponse.Builder().entity(response).headers(headers).build();
  }

  private static <K> BatchResponse<AnyRecord> toBatchResponse(Map<K, UpdateStatus> statuses, ProtocolVersion protocolVersion)
  {
    final DataMap splitResponseData = new DataMap(2);
    final DataMap splitStatuses = new DataMap();
    final DataMap splitErrors = new DataMap();

    for (Map.Entry<K, UpdateStatus> statusEntry : statuses.entrySet())
    {
      final UpdateStatus status = statusEntry.getValue();
      final String stringKey = URIParamUtils.encodeKeyForBody(statusEntry.getKey(), false, protocolVersion);

      final ErrorResponse error = status.getError();
      if (error == null)
      {
        // status and error should be mutually exclusive for now
        splitStatuses.put(stringKey, status.data());
      }
      else
      {
        splitErrors.put(stringKey, error.data());
      }
    }

    splitResponseData.put(BatchResponse.RESULTS, splitStatuses);
    splitResponseData.put(BatchResponse.ERRORS, splitErrors);

    return new BatchResponse<AnyRecord>(splitResponseData, AnyRecord.class);
  }
}
