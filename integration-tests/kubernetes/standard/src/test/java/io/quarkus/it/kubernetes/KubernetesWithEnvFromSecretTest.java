package io.quarkus.it.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class KubernetesWithEnvFromSecretTest {

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(GreetingResource.class))
            .setApplicationName("env-from-secret")
            .setApplicationVersion("0.1-SNAPSHOT")
            .withConfigurationResource("kubernetes-with-env-from-secret.properties");

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void assertGeneratedResources() throws IOException {
        Path kubernetesDir = prodModeTestResults.getBuildDir().resolve("kubernetes");
        assertThat(kubernetesDir)
                .isDirectoryContaining(p -> p.getFileName().endsWith("kubernetes.json"))
                .isDirectoryContaining(p -> p.getFileName().endsWith("kubernetes.yml"));
        List<HasMetadata> kubernetesList = DeserializationUtil
                .deserializeAsList(kubernetesDir.resolve("kubernetes.yml"));
        assertThat(kubernetesList.get(0)).isInstanceOfSatisfying(Deployment.class, d -> {
            assertThat(d.getMetadata()).satisfies(m -> {
                assertThat(m.getName()).isEqualTo("env-from-secret");
            });

            assertThat(d.getSpec()).satisfies(deploymentSpec -> {
                assertThat(deploymentSpec.getTemplate()).satisfies(t -> {
                    assertThat(t.getSpec()).satisfies(podSpec -> {
                        assertThat(podSpec.getContainers()).hasOnlyOneElementSatisfying(container -> {
                            assertThat(container.getEnvFrom())
                                    .filteredOn(env -> env.getSecretRef() != null
                                            && env.getSecretRef().getName() != null
                                            && env.getSecretRef().getName().equals("my-secret"))
                                    .hasOnlyOneElementSatisfying(env -> {
                                        assertThat(env.getSecretRef()).satisfies(secretRef -> {
                                            assertThat(secretRef.getOptional()).isNull();
                                        });
                                    });

                            assertThat(container.getEnv()).filteredOn(env -> "DB_PASSWORD".equals(env.getName()))
                                    .hasOnlyOneElementSatisfying(env -> {
                                        assertThat(env.getValueFrom()).satisfies(valueFrom -> {
                                            assertThat(valueFrom.getSecretKeyRef()).satisfies(secretKeyRef -> {
                                                assertThat(secretKeyRef.getKey()).isEqualTo("database.password");
                                                assertThat(secretKeyRef.getName()).isEqualTo("db-secret");
                                                assertThat(secretKeyRef.getOptional()).isNull();
                                            });
                                        });
                                    });
                        });
                    });
                });
            });
        });
    }
}