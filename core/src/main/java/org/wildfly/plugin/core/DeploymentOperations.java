/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.plugin.core;

import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_FULL_REPLACE_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_REDEPLOY_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_UNDEPLOY_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.NAME;
import static org.jboss.as.controller.client.helpers.ClientConstants.RUNTIME_NAME;
import static org.jboss.as.controller.client.helpers.ClientConstants.SERVER_GROUP;
import static org.jboss.as.controller.client.helpers.Operations.createAddOperation;
import static org.jboss.as.controller.client.helpers.Operations.createOperation;
import static org.jboss.as.controller.client.helpers.Operations.createRemoveOperation;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * A helper to create deployment operations.
 * <p>
 * Note that when creating operations deployment operations the
 * {@linkplain Deployment#getServerGroups() deployments server-groups} are used to determine if the deployment is for a
 * managed domain. If the server groups are {@linkplain Set#isEmpty() is empty} the standalone deployment operations
 * will be created. Otherwise deployment operations for managed domains will be created.
 * </p>
 * <p>
 * All operations create will be composite operations for consistency of parsing the result of executing the operation.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"unused", "StaticMethodOnlyUsedInOneClass", "WeakerAccess"})
public class DeploymentOperations {
    private static final String ENABLE = "enable";
    private static final String ENABLED = "enabled";
    static final ModelNode EMPTY_ADDRESS = new ModelNode().setEmptyList();

    static {
        EMPTY_ADDRESS.protect();
    }

    /**
     * Creates an {@linkplain ModelNode address} that can be used as the address for an operation. The address is
     * simply a {@link ModelNode} of type {@link ModelType#LIST}.
     * <p>
     * The string is split into key/value pairs. If the final key does not have a value an {@code *} is used to
     * indicate a  wildcard for the address.
     * </p>
     *
     * @param pairs the key/value pairs to use
     *
     * @return an address for the key/value pairs
     */
    public static ModelNode createAddress(final String... pairs) {
        return createAddress(Arrays.asList(pairs));
    }

    /**
     * Creates an {@linkplain ModelNode address} that can be used as the address for an operation. The address is
     * simply a {@link ModelNode} of type {@link ModelType#LIST}.
     * <p>
     * The string is split into key/value pairs. If the final key does not have a value an {@code *} is used to
     * indicate a  wildcard for the address.
     * </p>
     *
     * @param pairs the key/value pairs to use
     *
     * @return an address for the key/value pairs
     */
    public static ModelNode createAddress(final Iterable<String> pairs) {
        final ModelNode address = new ModelNode();
        final Iterator<String> iterator = pairs.iterator();
        while (iterator.hasNext()) {
            final String key = iterator.next();
            final String value = (iterator.hasNext() ? iterator.next() : "*");
            address.add(key, value);
        }
        return address;
    }

    /**
     * Creates an operation to add deployment content to the deployment repository.
     * <p>
     * Note this does not deploy the content.
     * </p>
     *
     * @param content     the path to the content to deploy
     * @param name        the name for the deployment content, if {@code null} the paths file name is used
     * @param runtimeName the runtime name for the deployment content
     *
     * @return the add operation
     */
    public static Operation createDeploymentAddOperation(final Path content, final String name, final String runtimeName) {
        Assertions.requiresNotNullParameter(content, "content");
        final ModelNode address = createAddress(DEPLOYMENT, name);
        final ModelNode addOperation = createAddOperation(address);
        if (runtimeName != null) {
            addOperation.get(RUNTIME_NAME).set(runtimeName);
        }
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create(true);
        DeploymentContent.of(content).addContentToOperation(builder, addOperation);
        builder.addStep(addOperation);
        return builder.build();
    }

    /**
     * Creates an operation to add deployment content to the deployment repository.
     * <p>
     * Note this does not deploy the content.
     * </p>
     *
     * @param content     the content to deploy
     * @param name        the name for the deployment content
     * @param runtimeName the runtime name for the deployment content, can be {@code null}
     *
     * @return the add operation
     */
    public static Operation createDeploymentAddOperation(final InputStream content, final String name, final String runtimeName) {
        Assertions.requiresNotNullParameter(content, "content");
        Assertions.requiresNotNullParameter(name, "name");
        final ModelNode address = createAddress(DEPLOYMENT, name);
        final ModelNode addOperation = createAddOperation(address);
        if (runtimeName != null) {
            addOperation.get(RUNTIME_NAME).set(runtimeName);
        }
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create(true);
        builder.addStep(addOperation);
        DeploymentContent.of(content).addContentToOperation(builder, addOperation);
        return builder.build();
    }

    /**
     * Creates an operation to deploy the content to a running server.
     *
     * @param deployment the deployment to deploy
     *
     * @return the deploy operation
     */
    public static Operation createDeployOperation(final Deployment deployment) {
        Assertions.requiresNotNullParameter(deployment, "deployment");
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create(true);
        addDeployOperationStep(builder, deployment);
        return builder.build();
    }

    /**
     * Creates an operation to deploy the content to a running server.
     *
     * @param deployments a set of deployments to deploy
     *
     * @return the deploy operation
     */
    public static Operation createDeployOperation(final Set<Deployment> deployments) {
        Assertions.requiresNotNullParameter(deployments, "deployments");
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create(true);
        for (Deployment deployment : deployments) {
            addDeployOperationStep(builder, deployment);
        }
        return builder.build();
    }

    /**
     * Creates an operation to replace deployment content to a running server. The previous content is undeployed, then
     * the new content is deployed, followed by the previous content being removed.
     *
     * @param deployment the deployment used to replace an existing deployment
     *
     * @return the deploy operation
     */
    public static Operation createReplaceOperation(final Deployment deployment) {
        Assertions.requiresNotNullParameter(deployment, "deployment");
        final ModelNode op = createOperation(DEPLOYMENT_FULL_REPLACE_OPERATION);
        op.get(NAME).set(deployment.getName());
        op.get(ENABLED).set(true);
        final String runtimeName = deployment.getRuntimeName();
        if (runtimeName != null) {
            op.get(RUNTIME_NAME).set(runtimeName);
        }
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create(true);
        addContent(builder, op, deployment);
        builder.addStep(op);
        return builder.build();
    }

    /**
     * Creates an operation to replace deployment content to a running server. The previous content is undeployed, then
     * the new content is deployed, followed by the previous content being removed.
     *
     * @param deployments the set deployment used to replace existing deployments which match the same name
     *
     * @return the deploy operation
     */
    public static Operation createReplaceOperation(final Set<Deployment> deployments) {
        Assertions.requiresNotNullParameter(deployments, "deployments");
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create(true);
        for (Deployment deployment : deployments) {
            addReplaceOperationSteps(builder, deployment);
        }
        return builder.build();
    }

    /**
     * Creates a redeploy operation for the deployment.
     * <p>
     * Note this does not upload new content. To add new content and deploy the new content see
     * {@link #createReplaceOperation(Deployment)}.
     * </p>
     *
     * @param deployment the deployment to redeploy
     *
     * @return the redeploy operation
     */
    public static Operation createRedeployOperation(final DeploymentDescription deployment) {
        Assertions.requiresNotNullParameter(deployment, "deployment");
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create(true);
        addRedeployOperationStep(builder, deployment);
        return builder.build();
    }

    /**
     * Creates a redeploy operation for the deployment.
     * <p>
     * Note this does not upload new content. To add new content and deploy the new content see
     * {@link #createReplaceOperation(Set)}.
     * </p>
     *
     * @param deployments the set of deployments to redeploy
     *
     * @return the redeploy operation
     */
    public static Operation createRedeployOperation(final Set<DeploymentDescription> deployments) {
        Assertions.requiresNotNullParameter(deployments, "deployments");
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create(true);
        for (DeploymentDescription deployment : deployments) {
            addRedeployOperationStep(builder, deployment);
        }
        return builder.build();
    }

    /**
     * Creates an undeploy operation. The operation will undeploy and remove the content.
     *
     * @param undeployDescription the description used to crate the operation
     *
     * @return the undeploy operation
     */
    public static Operation createUndeployOperation(final UndeployDescription undeployDescription) {
        Assertions.requiresNotNullParameter(undeployDescription, "undeployDescription");
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create(true);
        addUndeployOperationStep(builder, undeployDescription);
        return builder.build();
    }

    /**
     * Creates an undeploy operation. The operation will undeploy and remove the content.
     *
     * @param undeployDescriptions the set of descriptions used to crate the operation
     *
     * @return the undeploy operation
     */
    public static Operation createUndeployOperation(final Set<UndeployDescription> undeployDescriptions) {
        Assertions.requiresNotNullParameter(undeployDescriptions, "undeployDescriptions");
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create(true);
        for (UndeployDescription undeployDescription : undeployDescriptions) {
            addUndeployOperationStep(builder, undeployDescription);
        }
        return builder.build();
    }

    /**
     * Adds the deploy operation as a step to the composite operation.
     *
     * @param builder    the builder to add the step to
     * @param deployment the deployment to deploy
     */
    static void addDeployOperationStep(final CompositeOperationBuilder builder, final Deployment deployment) {
        final String name = deployment.getName();
        final ModelNode address = createAddress(DEPLOYMENT, name);
        final String runtimeName = deployment.getRuntimeName();
        final ModelNode addOperation = createAddOperation(address);
        if (runtimeName != null) {
            addOperation.get(RUNTIME_NAME).set(runtimeName);
        }
        addContent(builder, addOperation, deployment);
        builder.addStep(addOperation);

        final Set<String> serverGroups = deployment.getServerGroups();
        // If the server groups are empty this is a standalone deployment
        if (serverGroups.isEmpty()) {
            builder.addStep(createOperation(ClientConstants.DEPLOYMENT_DEPLOY_OPERATION, address));
        } else {
            for (String serverGroup : serverGroups) {
                final ModelNode sgAddress = createAddress(SERVER_GROUP, serverGroup, DEPLOYMENT, name);

                final ModelNode op = createAddOperation(sgAddress);
                op.get(ENABLE).set(true);
                if (runtimeName != null) {
                    op.get(RUNTIME_NAME).set(runtimeName);
                }
                builder.addStep(op);
            }
        }
    }

    /**
     * Adds a {@code full-replace-deployment} operation. If the {@code allowAddIfMissing} is set to {@code true} and
     * the {@linkplain Deployment#getServerGroups() server-groups} are not empty the content will be first added to the
     * server-group before the {@code full-replace-deployment} is executed.
     *
     * @param builder           the composite builder to add steps to
     * @param deployment        the deployment used for the replacement
     * @param currentDeployment the currently deployed application
     * @param allowAddIfMissing {@code true} if this should add deployments to server groups which they do not already
     *                          exist on, otherwise {@code false}
     */
    static void addReplaceOperationSteps(final CompositeOperationBuilder builder, final Deployment deployment,
                                         final DeploymentDescription currentDeployment, final boolean allowAddIfMissing) {
        final String name = deployment.getName();
        final String runtimeName = deployment.getRuntimeName();
        // Adds need to happen first on server-groups otherwise the full-replace-deployment will fail currently
        if (allowAddIfMissing) {
            // If deployment is not on the server group, add it but don't yet enable it. The full-replace-deployment
            // should handle that part.
            @SuppressWarnings("TypeMayBeWeakened")
            final Set<String> serverGroups = new LinkedHashSet<>(deployment.getServerGroups());
            if (!serverGroups.isEmpty()) {
                serverGroups.removeAll(currentDeployment.getServerGroups());
                for (String serverGroup : serverGroups) {
                    addDeploymentToServerGroupOperationStep(builder, name, runtimeName, serverGroup, false);
                }
            }
        }
        final ModelNode op = createOperation(DEPLOYMENT_FULL_REPLACE_OPERATION);
        op.get(NAME).set(name);
        if (runtimeName != null) {
            op.get(RUNTIME_NAME).set(runtimeName);
        }
        addContent(builder, op, deployment);
        op.get(ENABLED).set(true);
        builder.addStep(op);
    }

    /**
     * Adds a {@code full-replace-deployment} step for the deployment.
     *
     * @param builder    the builder to add the step to
     * @param deployment the deployment used to replace the existing deployment
     */
    static void addReplaceOperationSteps(final CompositeOperationBuilder builder, final Deployment deployment) {
        final String name = deployment.getName();
        final String runtimeName = deployment.getRuntimeName();
        final ModelNode op = createOperation(DEPLOYMENT_FULL_REPLACE_OPERATION);
        op.get(NAME).set(name);
        if (runtimeName != null) {
            op.get(RUNTIME_NAME).set(runtimeName);
        }
        addContent(builder, op, deployment);
        op.get(ENABLED).set(true);
        builder.addStep(op);
    }

    /**
     * Adds a redeploy step to the composite operation.
     *
     * @param builder    the builder to add the step to
     * @param deployment the deployment being redeployed
     */
    private static void addRedeployOperationStep(final CompositeOperationBuilder builder, final DeploymentDescription deployment) {
        final String deploymentName = deployment.getName();
        final Set<String> serverGroups = deployment.getServerGroups();
        if (serverGroups.isEmpty()) {
            builder.addStep(createOperation(DEPLOYMENT_REDEPLOY_OPERATION, createAddress(DEPLOYMENT, deploymentName)));
        } else {
            for (String serverGroup : serverGroups) {
                builder.addStep(createOperation(DEPLOYMENT_REDEPLOY_OPERATION, createAddress(SERVER_GROUP, serverGroup, DEPLOYMENT, deploymentName)));
            }
        }
    }

    private static void addUndeployOperationStep(final CompositeOperationBuilder builder,
                                                 @SuppressWarnings("TypeMayBeWeakened") final UndeployDescription undeployDescription) {
        final String name = undeployDescription.getName();
        final Set<String> serverGroups = undeployDescription.getServerGroups();
        if (serverGroups.isEmpty()) {
            final ModelNode address = createAddress(DEPLOYMENT, name);
            builder.addStep(createOperation(DEPLOYMENT_UNDEPLOY_OPERATION, address))
                    .addStep(createRemoveOperation(address));
        } else {
            for (String serverGroup : serverGroups) {
                final ModelNode address = createAddress(SERVER_GROUP, serverGroup, DEPLOYMENT, name);
                builder.addStep(createOperation(DEPLOYMENT_UNDEPLOY_OPERATION, address))
                        .addStep(createRemoveOperation(address));
            }
            builder.addStep(createRemoveOperation(createAddress(DEPLOYMENT, name)));
        }
    }


    private static void addDeploymentToServerGroupOperationStep(final CompositeOperationBuilder builder, final String name,
                                                                final String runtimeName, final String serverGroup,
                                                                final boolean enabled) {
        final ModelNode sgAddress = createAddress(SERVER_GROUP, serverGroup, DEPLOYMENT, name);
        final ModelNode addOp = createAddOperation(sgAddress);
        addOp.get(ENABLED).set(enabled);
        if (runtimeName != null) {
            addOp.get(RUNTIME_NAME).set(runtimeName);
        }
        builder.addStep(addOp);
    }

    private static void addContent(final CompositeOperationBuilder builder, final ModelNode op, final Deployment deployment) {
        deployment.getContent().addContentToOperation(builder, op);
    }
}
