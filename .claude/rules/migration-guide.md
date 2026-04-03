# 旧システム（stock-app）からの移行ガイド

## 主要変更点
| 項目 | 旧（stock-app） | 新（oda-data-hub） |
|------|----------------|-------------------|
| フロントエンド | Thymeleaf + jQuery + Bootstrap 4 | React + TypeScript + shadcn/ui |
| API層 | @Controller（テンプレート返却）| @RestController（JSON API）|
| Spring Boot | 2.1.1.RELEASE | 3.3.x |
| Java | 指定なし | 21 |
| 名前空間 | javax.* | jakarta.* |
| Spring Batch | JobBuilderFactory / StepBuilderFactory | JobBuilder / StepBuilder + JobRepository |
| Spring Security | WebSecurityConfigurerAdapter | @Bean SecurityFilterChain |
| Spring Data JPA | getOne() | getReferenceById() / findById() |
| Hibernate | 5.x (PostgreSQL95Dialect) | 6.x (自動検出、明示指定不要) |
| PostgreSQL | 9.6 | 17 |
| hibernate-types | hibernate-types-52 | hypersistence-utils-hibernate-63 |

## UIコンポーネントマッピング
| 旧 | 新 |
|---|---|
| Bootstrap Table + DataTables | TanStack Table + shadcn/ui Table |
| jQuery AJAX | TanStack Query |
| Bootstrap Modal / window.open() | shadcn/ui Dialog |
| Bootstrap Form + @Validated | React Hook Form + Zod |
| Bootstrap Select + Select2 | SearchableSelect（Popover + Command/cmdk） |
| Bootstrap DateTimePicker | shadcn/ui DatePicker (date-fns) |
| FontAwesome | Lucide React |
| Chart.js / ApexCharts | Recharts |
| Thymeleaf th:if / th:each | React 条件レンダリング / map() |

## 並行運用
- 旧システム（stock-app）は C:\project\stock-app で運用継続
- 同一データベースを参照（移行期間中）
- 機能単位で段階的に新システムへ切り替え
