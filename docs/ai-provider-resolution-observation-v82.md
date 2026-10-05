# AI 服务解析与观察顺序 V82

## 用户可见缺口

V81 会在活动请求使用的精确服务被删除或重配时停止后续 AI 阶段。不过，Room 的数据库读取与 `Flow` 回调并不保证同一时刻抵达：请求可能已经从当前数据库快照解析到新 revision，而 ViewModel 仍暂存着上一次回调的旧 revision。若直接比较两者，应用会把这个过期观察误判成运行中重配，停止一个实际使用最新配置的新请求。

## 边界修正

- ViewModel 为服务 `Flow` 回调维护仅存内存的递增观察序列。
- 接受请求并开始读取当前数据库快照前固定该序列。
- 数据库返回服务身份与配置后，只有解析期间确实到达了新的 `Flow` 观察，且它显示精确服务已消失或 revision 不同，才立即停止。
- 解析前遗留的旧观察不能否决当前数据库快照；之后到达的每个新观察仍由 V81 的持续监听检查并可停止请求。
- 默认服务切换、本次服务身份显示、API key 边界、模型协议、取消代次、V76/V77 规则检查点、保存事务、输入状态与浏览器会话均保持不变。

## 回归约束

- `AiProviderResolutionObservationV82Test#staleObservationFromBeforeResolutionCannotStopTheCurrentDatabaseRevision`
- `AiProviderResolutionObservationV82Test#observationDeliveredDuringResolutionStillStopsADeletedOrReconfiguredService`
- `SourceBrowserSessionV56DeviceTest#staleProviderObservationDoesNotStopANewlyResolvedRevision`

设备回归使用隔离 Room 服务和原创 HTML 站点，先保存旧 revision、再真实重配服务，并只把 ViewModel 的缓存观察回退为旧值。请求必须从数据库解析并显示新服务身份、进入真实网站读取，不能出现 V81 的服务变更停止提示。测试随后显式取消，确保没有扩大后台工作或网络权限。

## 明确限制

这项修正只消除数据库当前快照与旧 `Flow` 缓存的顺序误判。真实 AI、远端已接受请求的撤回、真实公网错误、第三方登录/验证码、真机、API 28 渲染回收、真实分屏/自由窗口、外部导入波动、历史 `device offline` 与 Compose 首布局竞态仍未验证。
