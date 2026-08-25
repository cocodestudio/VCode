package com.cocode.vcode.ide.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.cocode.vcode.ide.data.model.Result;
import com.cocode.vcode.ide.utils.ExecutorProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FileRepositoryTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private FileRepository fileRepository;

    @Before
    public void setUp() {
        fileRepository = new FileRepository();
        // Since FileRepository uses ExecutorProvider, we should ideally use a synchronous executor for tests.
        // However, we can also use CountDownLatch to wait for LiveData to emit if the ExecutorProvider uses IO threads.
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testWriteFileSync() throws IOException {
        File file = tempFolder.newFile("test.txt");
        fileRepository.writeFileSync(file, "Hello World");

        String content = new String(Files.readAllBytes(file.toPath()));
        assertEquals("Hello World", content);
    }

    @Test
    public void testWriteFileAsync() throws Exception {
        File file = tempFolder.newFile("async_test.txt");
        LiveData<Result<Boolean>> liveData = fileRepository.writeFile(file, "Async content");

        CountDownLatch latch = new CountDownLatch(1);
        final Result<Boolean>[] resultHolder = new Result[1];
        
        // Wait for Robolectric's main thread to idle and the IO thread to complete
        liveData.observeForever(result -> {
            resultHolder[0] = result;
            latch.countDown();
        });

        // Run Robolectric's background scheduler since IO is submitted there
        // Actually, ExecutorProvider.getInstance().runOnIo() might spawn real threads or use Robolectric's scheduler.
        // If it uses real threads, latch will work.
        // Try looping with ShadowLooper to pump the UI thread if ExecutorProvider posts there
        for(int i=0; i<50; i++) {
            if(latch.await(100, TimeUnit.MILLISECONDS)) break;
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        }
        
        assertNotNull("Result should not be null", resultHolder[0]);
        assertTrue(resultHolder[0].isSuccess());
        String content = new String(Files.readAllBytes(file.toPath()));
        assertEquals("Async content", content);
    }
}
