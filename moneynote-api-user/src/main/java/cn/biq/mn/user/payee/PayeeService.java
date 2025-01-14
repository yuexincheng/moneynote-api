package cn.biq.mn.user.payee;

import cn.biq.mn.user.base.BaseService;
import cn.biq.mn.user.book.Book;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import cn.biq.mn.base.exception.FailureMessageException;
import cn.biq.mn.base.exception.ItemExistsException;
import cn.biq.mn.user.balanceflow.BalanceFlowRepository;
import cn.biq.mn.user.utils.Limitation;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class PayeeService {

    private final PayeeRepository payeeRepository;
    private final BaseService baseService;
    private final BalanceFlowRepository balanceFlowRepository;

    public PayeeDetails add(PayeeAddForm form) {
        Book book = baseService.findBookById(form.getBookId());
        // 限制每个账本的交易对象数量
        if (payeeRepository.countByBook(book) >= Limitation.payee_max_count) {
            throw new FailureMessageException("payee.max.count");
        }
        // 不能重复
        if (payeeRepository.existsByBookAndName(book, form.getName())) {
            throw new ItemExistsException();
        }
        Payee entity = PayeeMapper.toEntity(form);
        entity.setBook(book);
        payeeRepository.save(entity);
        return PayeeMapper.toDetails(entity);
    }

    @Transactional(readOnly = true)
    public Page<PayeeDetails> query(PayeeQueryForm form, Pageable page) {
        // 确保传入的bookId是自己组里面的。
        baseService.findBookById(form.getBookId());
        Page<Payee> entityPage = payeeRepository.findAll(form.buildPredicate(), page);
        return entityPage.map(PayeeMapper::toDetails);
    }

    @Transactional(readOnly = true)
    public List<PayeeDetails> queryAll(PayeeQueryForm form) {
        if (form.getBookId() == null) {
            return new ArrayList<>();
        }
        // 确保传入的bookId是自己组里面的。
        Book book = baseService.findBookById(form.getBookId());
        form.setEnable(true);
        List<Payee> entityList = payeeRepository.findAll(form.buildPredicate());
        Payee keep = baseService.findPayeeById(form.getKeep());
        if (keep != null && !entityList.contains(keep)) {
            if (keep.getBook().getId().equals(book.getId())) {
                entityList.add(0, keep);
            }
        }
        return entityList.stream().map(PayeeMapper::toDetails).toList();
    }

    public boolean toggle(Integer id) {
        Payee entity = baseService.findPayeeById(id);
        entity.setEnable(!entity.getEnable());
        payeeRepository.save(entity);
        return true;
    }

    public boolean toggleCanExpense(Integer id) {
        Payee entity = baseService.findPayeeById(id);
        entity.setCanExpense(!entity.getCanExpense());
        payeeRepository.save(entity);
        return true;
    }

    public boolean toggleCanIncome(Integer id) {
        Payee entity = baseService.findPayeeById(id);
        entity.setCanIncome(!entity.getCanIncome());
        payeeRepository.save(entity);
        return true;
    }

    public boolean update(Integer id, PayeeUpdateForm form) {
        Payee entity = baseService.findPayeeById(id);
        Book book = entity.getBook();
        if (!entity.getName().equals(form.getName())) {
            if (StringUtils.hasText(form.getName())) {
                if (payeeRepository.existsByBookAndName(book, form.getName())) {
                    throw new ItemExistsException();
                }
            }
        }
        PayeeMapper.updateEntity(form, entity);
        payeeRepository.save(entity);
        return true;
    }

    public boolean remove(Integer id) {
        Payee entity = baseService.findPayeeById(id);
        // 有账单关联
        if (balanceFlowRepository.existsByPayee(entity)) {
            throw new FailureMessageException("payee.delete.has.flow");
        }
        payeeRepository.delete(entity);
        return true;
    }

}
