package jp.co.oda32.batch.purchase;

import jp.co.oda32.domain.model.master.MShopLinkedFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.file.MultiResourceItemReader;
import org.springframework.batch.item.file.ResourceAwareItemReaderItemStream;
import org.springframework.core.io.Resource;

import java.nio.file.Paths;
import java.util.List;

/**
 * 複数の SMILE 仕入 CSV を順次読み、各行に対応するショップ番号を付与する ItemReader。
 *
 * <p>{@link MultiResourceItemReader} は現在読込中の {@link Resource} を公開 API として
 * 提供しないため、{@link #setDelegate(ResourceAwareItemReaderItemStream)} で設定される
 * delegate を本クラス側でラップし、{@code setResource()} の呼び出しを契機に現在リソースを
 * 追跡する。読み出した 1 行ごとに、追跡中のファイル名を {@code m_shop_linked_file} と
 * 突合して shop_no を確定させる。
 *
 * <p><b>重要</b>: 複数ファイル（例: shop_no=1 の {@code purchase_import.csv} と
 * shop_no=2 の {@code purchase_import2_YYYYMMDD.csv}）は処理連番(shori_renban) の
 * 番号帯が別系統。「今どのファイルを読んでいるか」に基づく shop_no 設定が必須。過去は
 * {@code resources[]} 配列先頭から一致検索していたため全行が先頭ファイルの shop_no に
 * なるバグがあり、shop_no=2 で登録すべき行が shop_no=1 にも複製される原因となっていた。
 */
@Slf4j
public class ShopNoAwareItemReader extends MultiResourceItemReader<PurchaseFile> {

    private final List<MShopLinkedFile> shopLinkedFileList;
    /** 現在読み込み中の Resource（delegate の setResource 呼び出しで更新）。 */
    private volatile Resource currentResource;

    public ShopNoAwareItemReader(List<MShopLinkedFile> shopLinkedFileList) {
        this.shopLinkedFileList = shopLinkedFileList;
    }

    /**
     * delegate を差し替える際に、setResource をフックして currentResource を追跡するラッパーを被せる。
     * <p>unchecked キャストを避けるため、ラッパーは内部 delegate を
     * {@code ? extends PurchaseFile} のまま保持し、{@code read()} で
     * {@code PurchaseFile} に安全にアップキャストする。
     */
    @Override
    public void setDelegate(ResourceAwareItemReaderItemStream<? extends PurchaseFile> delegate) {
        super.setDelegate(new ResourceTrackingDelegate(delegate));
    }

    @Override
    public PurchaseFile read() throws Exception {
        PurchaseFile purchaseFile = super.read();
        if (purchaseFile == null) {
            return null;
        }
        // 既に shop_no がセット済みであれば尊重（CSV 側で持たせる拡張互換）。
        if (purchaseFile.getShopNo() != 0) {
            return purchaseFile;
        }
        Resource resource = this.currentResource;
        if (resource == null || resource.getFilename() == null) {
            log.warn("読込中 Resource を特定できませんでした。shop_no 未設定で返却します。");
            return purchaseFile;
        }
        String filename = resource.getFilename();
        MShopLinkedFile matched = shopLinkedFileList.stream()
                .filter(f -> filename.equals(
                        Paths.get(f.getSmilePurchaseFileName()).getFileName().toString()))
                .findFirst()
                .orElse(null);
        if (matched != null) {
            purchaseFile.setShopNo(matched.getShopNo());
        } else {
            log.warn("m_shop_linked_file に対応するファイル名が見つかりません: {}", filename);
        }
        return purchaseFile;
    }

    /**
     * {@link MultiResourceItemReader} がファイル切替時に呼ぶ {@code setResource()} を
     * フックして現在 Resource を記録する delegate ラッパー。他メソッドは内包 delegate に委譲。
     * <p>内部 delegate は {@code ? extends PurchaseFile} のまま保持し、
     * {@code read()} は安全なアップキャストで {@code PurchaseFile} を返す。
     */
    private final class ResourceTrackingDelegate
            implements ResourceAwareItemReaderItemStream<PurchaseFile> {
        private final ResourceAwareItemReaderItemStream<? extends PurchaseFile> inner;

        ResourceTrackingDelegate(ResourceAwareItemReaderItemStream<? extends PurchaseFile> inner) {
            this.inner = inner;
        }

        @Override
        public void setResource(Resource resource) {
            currentResource = resource;
            inner.setResource(resource);
        }

        @Override
        public PurchaseFile read() throws Exception {
            return inner.read();
        }

        @Override
        public void open(ExecutionContext executionContext) throws ItemStreamException {
            inner.open(executionContext);
        }

        @Override
        public void update(ExecutionContext executionContext) throws ItemStreamException {
            inner.update(executionContext);
        }

        @Override
        public void close() throws ItemStreamException {
            inner.close();
        }
    }
}
