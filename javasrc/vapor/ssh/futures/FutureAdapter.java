package vapor.ssh.futures;

import com.google.common.util.concurrent.AbstractFuture;
import org.apache.sshd.common.future.SshFuture;
import org.apache.sshd.common.future.SshFutureListener;

/**
 * Created by cmarshall on 10/30/14.
 */
public class FutureAdapter<T extends SshFuture> extends AbstractFuture<T> implements SshFutureListener<T>
{
    final T future;

    public FutureAdapter(T future)
    {
        this.future = future;
        this.future.addListener(this);
    }

    @Override
    public void operationComplete(T future) {
        set(future);
    }
}
