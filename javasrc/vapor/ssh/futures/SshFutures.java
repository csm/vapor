package vapor.ssh.futures;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.sshd.ClientChannel;
import org.apache.sshd.ClientSession;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.common.future.CloseFuture;

/**
 * Created by cmarshall on 10/30/14.
 */
public class SshFutures
{
    public static ListenableFuture<ClientSession> connectFuture(ConnectFuture future)
    {
        ListenableFuture<ConnectFuture> adapter = new FutureAdapter<ConnectFuture>(future) {
            @Override
            public void operationComplete(ConnectFuture future) {
                if (future.getException() != null)
                    setException(future.getException());
                else
                    super.operationComplete(future);
            }
        };
        return Futures.transform(adapter, new Function<ConnectFuture, ClientSession>() {
            @Override
            public ClientSession apply(ConnectFuture input) {
                return input.getSession();
            }
        });
    }

    public static ListenableFuture<Boolean> openFuture(OpenFuture future)
    {
        ListenableFuture<OpenFuture> adapter = new FutureAdapter<OpenFuture>(future)
        {
            @Override
            public void operationComplete(OpenFuture future) {
                if (future.getException() != null)
                    setException(future.getException());
                else
                    super.operationComplete(future);
            }
        };
        return Futures.transform(adapter, new Function<OpenFuture, Boolean>() {
            @Override
            public Boolean apply(OpenFuture input) {
                return input.isOpened();
            }
        });
    }

    public static ListenableFuture<Boolean> authFuture(AuthFuture future) {
        ListenableFuture<AuthFuture> adapter = new FutureAdapter<AuthFuture>(future) {
            @Override
            public void operationComplete(AuthFuture future) {
                if (future.getException() != null)
                    setException(future.getException());
                else
                    super.operationComplete(future);
            }
        };
        return Futures.transform(adapter, new Function<AuthFuture, Boolean>() {
            @Override
            public Boolean apply(AuthFuture input) {
                return input.isSuccess();
            }
        });
    }

    public static ListenableFuture<Boolean> closeFuture(CloseFuture future) {
        ListenableFuture<CloseFuture> adapter = new FutureAdapter<CloseFuture>(future);
        return Futures.transform(adapter, new Function<CloseFuture, Boolean>() {
            @Override
            public Boolean apply(CloseFuture closeFuture) {
                return closeFuture.isClosed();
            }
        });
    }
}
